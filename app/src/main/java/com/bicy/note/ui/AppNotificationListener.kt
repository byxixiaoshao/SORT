package com.bicy.note.ui

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bicy.note.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通知监听：记录用户指定的应用（monitoredPackages）发来的通知。
 * 不按重要性过滤——不想要的应用，用户直接在「监听应用」里移除即可。
 * 记录写入当天并以星标标记，一天之后会被自动移除。
 *
 * 独立进程（android:process=":listener"）运行：主进程被系统节能策略冻结/杀掉时，
 * 本服务仍由系统绑定持有，通知照常接收并写入 JSON 文件。
 * 写入完成后向主进程发 ACTION_DATA_CHANGED 广播，主进程据此刷新界面与设置。
 *
 * 排查用 logcat 标签：[TAG]，全链路日志：
 * 连接/断开、收到的每条通知、命中监控应用后的标题正文、写入结果。
 */
class AppNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(
            TAG,
            "onCreate: 监听服务已创建 进程=${Process.myProcessName()} pid=${Process.myPid()}",
        )
    }

        override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(
            TAG,
            "onListenerConnected: 已连接，开始接收通知回调 " +
                "进程=${Process.myProcessName()} pid=${Process.myPid()}",
        )
        rebindAttempts = 0
        runCatching { NoteRepository.get(this).updateSetting("listenerConnected", true) }
        sendDataChangedBroadcast()
        scope.launch { recoverActiveNotifications() }
    }

    /**
     * 连接时拉取当前活动通知列表，补记断连期间错过的消息
     * （进程被杀/监听断开时发出的通知，重新连接后从这里恢复）。
     * 只恢复 30 分钟内发布的，避免把很久以前的旧通知当成新消息。
     */
    private suspend fun recoverActiveNotifications() {
        runCatching {
            val repository = NoteRepository.get(this)
            val settings = repository.currentSettings()
            val active = activeNotifications ?: run {
                Log.w(TAG, "恢复活动通知: 无法获取活动通知列表")
                return
            }
            val deadline = System.currentTimeMillis() - 30 * 60 * 1000L
            val missed = active.filter {
                it.packageName in settings.monitoredPackages && it.postTime >= deadline
            }
            Log.d(TAG, "恢复活动通知: 活动通知共 ${active.size} 个，其中监控应用近30分钟 ${missed.size} 个")
            var wrote = false
            missed.forEach { sbn ->
                val (title, body) = extractText(sbn)
                if (title.isBlank() && body.isBlank()) return@forEach
                if (repository.addNotificationEntry(sbn.packageName, title, body)) {
                    wrote = true
                    Log.i(TAG, "已补记断连期间的通知: pkg=${sbn.packageName} title=\"$title\" body=\"$body\"")
                }
            }
            if (wrote) sendDataChangedBroadcast()
        }.onFailure {
            Log.w(TAG, "恢复活动通知失败", it)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "onListenerDisconnected: 监听断开，尝试强制重连")
        runCatching { NoteRepository.get(this).updateSetting("listenerConnected", false) }
        sendDataChangedBroadcast()
        scope.launch {
            rebindAttempts++
            when (rebindAttempts) {
                1 -> delay(0)
                2 -> delay(15_000)
                3 -> delay(60_000)
                else -> {
                    Log.w(TAG, "已连续 3 次强制重连失败，停止自动重试（需重启手机或手动强制重连）")
                    return@launch
                }
            }
            forceRebind(this@AppNotificationListener)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val repository = NoteRepository.get(this)
        val settings = repository.currentSettings()
        Log.i(
            TAG,
            "收到通知: pkg=${sbn.packageName} postTime=${sbn.postTime} " +
                "监听开关=${settings.notificationListening} 监控列表=${settings.monitoredPackages}",
        )
        if (!settings.notificationListening) {
            Log.w(TAG, "忽略: 通知监听开关未开启")
            return
        }
        if (sbn.packageName !in settings.monitoredPackages) {
            Log.d(TAG, "忽略: ${sbn.packageName} 不在监控列表")
            return
        }
        val (title, body) = extractText(sbn)
        Log.i(TAG, "命中监控应用 ${sbn.packageName}: title=\"$title\" body=\"$body\"")
        if (title.isBlank() && body.isBlank()) {
            Log.w(
                TAG,
                "忽略: 标题与正文均为空，通知 extras 键=${sbn.notification.extras.keySet()}",
            )
            return
        }
        scope.launch {
            val written = repository.addNotificationEntry(sbn.packageName, title, body)
            if (written) {
                Log.i(TAG, "已写入今日记录: pkg=${sbn.packageName} title=\"$title\" body=\"$body\"")
            }
            sendDataChangedBroadcast()
        }
    }

    /** 数据/设置变化后广播给主进程（主进程收到后重读磁盘刷新界面与设置）。 */
    private fun sendDataChangedBroadcast() {
        runCatching {
            sendBroadcast(
                Intent(NoteRepository.ACTION_DATA_CHANGED).setPackage(packageName)
            )
            Log.d(TAG, "已广播数据变更通知给主进程")
        }.onFailure {
            Log.w(TAG, "广播数据变更通知失败", it)
        }
    }

    /**
     * 从通知中提取标题与正文。部分应用（聊天类）会把内容放在
     * EXTRA_TEXT_LINES（收件箱样式）或 EXTRA_BIG_TEXT 里，逐级回退。
     */
    private fun extractText(sbn: StatusBarNotification): Pair<String, String> {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (body.isBlank()) {
            body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        }
        if (body.isBlank()) {
            body = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.filterNotNull()
                ?.joinToString("\n")
                .orEmpty()
        }
        return title to body
    }

    companion object {
        private const val TAG = "寄意通知监听"

        /** 断连后的强制重连尝试次数（进程内），成功连接后清零。 */
        private var rebindAttempts = 0

        /**
         * 强制重绑通知监听，替代「重启手机」：
         * 1. 优先直接改写 enabled_notification_listeners（需 WRITE_SECURE_SETTINGS，
         *    一次 adb pm grant 永久生效）：先移除自己再写回，系统会立即重新绑定；
         * 2. 否则组件禁用再启用（DONT_KILL_APP）+ requestRebind，强制系统重新枚举监听组件。
         * 单独 requestRebind 在部分 ROM 上会被无视，此序列可解决「必须重启才生效」。
         */
        fun forceRebind(context: Context) {
            val cn = ComponentName(context, AppNotificationListener::class.java)
            if (toggleViaSecureSettings(context, cn)) {
                Log.i(TAG, "forceRebind: 已通过改写 enabled_notification_listeners 完成重绑（无需重启）")
                return
            }
            val pm = context.packageManager
            val handler = Handler(Looper.getMainLooper())
            Log.i(TAG, "forceRebind: 无 WRITE_SECURE_SETTINGS 权限，改用组件禁用再启用 + requestRebind")
            runCatching {
                pm.setComponentEnabledSetting(
                    cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                )
            }
            handler.postDelayed({
                runCatching {
                    pm.setComponentEnabledSetting(
                        cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
                    )
                }
                handler.postDelayed({ requestRebind(cn) }, 300)
            }, 300)
        }

        /** 直接改写系统「已启用的通知监听」列表：移除自己再写回，触发系统立即重绑。 */
        private fun toggleViaSecureSettings(context: Context, cn: ComponentName): Boolean {
            return try {
                val resolver = context.contentResolver
                val key = "enabled_notification_listeners"
                val original = Settings.Secure.getString(resolver, key).orEmpty()
                val component = cn.flattenToString()
                val entries = original.split(":").filter { it.isNotBlank() }.toMutableList()
                entries.remove(component)
                Settings.Secure.putString(resolver, key, entries.joinToString(":"))
                entries.add(component)
                Settings.Secure.putString(resolver, key, entries.joinToString(":"))
                true
            } catch (e: SecurityException) {
                false
            } catch (e: Exception) {
                Log.w(TAG, "改写监听列表失败，回退组件重启用", e)
                false
            }
        }
    }
}