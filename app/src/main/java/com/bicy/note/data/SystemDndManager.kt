package com.bicy.note.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * 系统勿扰：读取/切换系统勿扰的当前开关状态。
 * 需要用户授予勿扰权限（isNotificationPolicyAccessGranted）才能读写。
 */
object SystemDndManager {

    private const val TAG = "寄意勿扰"

    /** 用户是否已授予勿扰权限（系统设置 → 勿扰 → 允许应用更改勿扰设置） */
    fun isAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    /** 打开系统勿扰权限授权页 */
    fun openAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    /**
     * 自授勿扰访问权限：勿扰白名单存在 Settings.Secure 的
     * enabled_notification_policy_access_packages 字段（官方 cmd notification set_dnd_access_packages
     * 改的就是它）。持有 WRITE_SECURE_SETTINGS（adb 授予）的应用可直接写入，绕开部分 ROM
     * 藏掉勿扰授权入口的问题；写入后立即生效且开机保留。
     *
     * @return 是否已获得勿扰访问权限
     */
    fun selfGrantAccess(context: Context): Boolean {
        if (isAccessGranted(context)) return true
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "未持有 WRITE_SECURE_SETTINGS，无法自授勿扰权限")
            return false
        }
        val ok = runCatching {
            val current = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_policy_access_packages"
            )
            val packages = (current ?: "").split(":").filter { it.isNotBlank() }.toMutableList()
            if (context.packageName !in packages) {
                packages.add(context.packageName)
                Settings.Secure.putString(
                    context.contentResolver,
                    "enabled_notification_policy_access_packages",
                    packages.joinToString(":"),
                )
                Log.d(TAG, "已通过 WRITE_SECURE_SETTINGS 写入勿扰白名单")
            }
        }.isSuccess
        return ok && isAccessGranted(context)
    }

    /** 系统勿扰当前是否开启：当前过滤级别不是「全部允许」即视为开启（含静音/仅闹钟/仅优先） */
    fun isDndActive(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try {
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            Log.e(TAG, "读取当前勿扰状态失败", e)
            false
        }
    }

    /**
     * 开启/关闭系统勿扰。
     * @return null 表示成功；非 null 为错误信息（如无权限或系统不允许第三方切换）
     */
    fun setDnd(context: Context, active: Boolean): String? {
        if (!isAccessGranted(context)) return "未授予勿扰权限"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching {
            nm.setInterruptionFilter(
                if (active) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            Log.d(TAG, "已设置系统勿扰: ${if (active) "开启" else "关闭"}")
            null
        }.getOrElse { e ->
            Log.e(TAG, "设置系统勿扰失败", e)
            e.message ?: e.javaClass.simpleName
        }
    }
}