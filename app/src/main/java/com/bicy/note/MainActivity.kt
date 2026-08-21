package com.bicy.note

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.NoteRepository
import com.bicy.note.ui.AppNavigation
import com.bicy.note.ui.AppNotificationListener
import com.bicy.note.ui.QuickNoteService
import com.bicy.note.ui.ScheduledDnd
import com.bicy.note.ui.theme.寄意Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = NoteRepository.get(applicationContext)
        bootstrap(repository)
        setContent {
            CompositionLocalProvider(LocalRepository provides repository) {
                寄意Theme {
                    AppNavigation()
                }
            }
        }
        // 监听进程（独立进程）写入数据/更新设置后广播，主进程据此重读磁盘刷新界面
        ContextCompat.registerReceiver(
            this,
            dataChangedReceiver,
            IntentFilter(NoteRepository.ACTION_DATA_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        unregisterReceiver(dataChangedReceiver)
        super.onDestroy()
    }

    /** 通知监听进程（:listener）发来的数据变更广播。 */
    private val dataChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NoteRepository.ACTION_DATA_CHANGED) return
            val repository = NoteRepository.get(this@MainActivity)
            repository.reloadSettings()
            repository.bumpNotesVersion()
        }
    }

    /**
     * 应用启动时按已保存的设置恢复后台功能：
     * - 随时记：悬浮球服务（有悬浮窗权限才启动）
     * - 通知监听：未连接时触发强制重绑（部分 ROM 授权后不主动绑定，普通 requestRebind 被无视）
     * 设置页里同样的逻辑保留，用于从系统授权页返回后兜底。
     */
    private fun bootstrap(repository: NoteRepository) {
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = repository.currentSettings()
            if (settings.quickRecordEnabled && Settings.canDrawOverlays(this@MainActivity)) {
                startService(Intent(this@MainActivity, QuickNoteService::class.java))
            }
            ScheduledDnd.arm(this@MainActivity)
            if (settings.notificationListening && !settings.listenerConnected) {
                AppNotificationListener.forceRebind(this@MainActivity)
            }
        }
    }
}