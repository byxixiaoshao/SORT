package com.bicy.note.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bicy.note.data.LocalRepository
import com.bicy.note.ui.AppNotificationListener
import com.bicy.note.ui.LocalSettingsOverlayHost
import com.bicy.note.ui.QuickNoteService
import com.bicy.note.ui.components.ScreenHeader
import com.bicy.note.ui.screens.settings.MonitoredAppsPopup
import com.bicy.note.ui.screens.settings.SettingCategorySection
import com.bicy.note.ui.screens.settings.SettingClickItem
import com.bicy.note.ui.screens.settings.SettingClickItemWithIcon
import com.bicy.note.ui.screens.settings.SettingSliderWithTextFieldItem
import com.bicy.note.ui.screens.settings.SettingSwitchItem
import com.bicy.note.ui.screens.settings.SettingTextItem
import com.bicy.note.ui.screens.settings.SettingTextItemWithButton
import com.bicy.note.ui.screens.settings.ThemePickerPopup
import com.bicy.note.ui.screens.settings.themePresetName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repository = LocalRepository.current
    val settings by repository.settings.collectAsStateWithLifecycle()
    val overlayHost = LocalSettingsOverlayHost.current
    val scope = rememberCoroutineScope()
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    var accessGranted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    var pendingQuickRecord by remember { mutableStateOf(false) }
    var pendingNotification by remember { mutableStateOf(false) }

    val appListPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestAppListPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.QUERY_ALL_PACKAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            appListPermissionLauncher.launch(Manifest.permission.QUERY_ALL_PACKAGES)
        }
    }

    LifecycleResumeEffect(Unit) {
        // 从系统授权页返回后：按实际授权结果决定开关最终值
        if (pendingQuickRecord) {
            pendingQuickRecord = false
            if (Settings.canDrawOverlays(context)) {
                repository.updateSetting("quickRecordEnabled", true)
                context.startService(Intent(context, QuickNoteService::class.java))
                Toast.makeText(context, "悬浮窗权限已授予，随时记已开启", Toast.LENGTH_SHORT).show()
            } else {
                repository.updateSetting("quickRecordEnabled", false)
                Toast.makeText(context, "未授予悬浮窗权限，随时记未开启", Toast.LENGTH_LONG).show()
            }
        }
        if (pendingNotification) {
            pendingNotification = false
            if (isNotificationAccessGranted(context)) {
                repository.updateSetting("notificationListening", true)
                Toast.makeText(context, "通知使用权已授予，通知监听已开启", Toast.LENGTH_SHORT).show()
            } else {
                repository.updateSetting("notificationListening", false)
                Toast.makeText(context, "未授予通知使用权，通知监听未开启", Toast.LENGTH_LONG).show()
            }
        }
        accessGranted = isNotificationAccessGranted(context)
        Log.d(TAG, "设置页可见: 通知使用权已授权=$accessGranted 监听已连接=${settings.listenerConnected}")
        if (settings.notificationListening && accessGranted && !settings.listenerConnected) {
            AppNotificationListener.forceRebind(context)
            Log.d(TAG, "监听未连接，已触发强制重连（无需重启）")
            scope.launch {
                delay(3000)
                if (!repository.currentSettings().listenerConnected) {
                    Toast.makeText(
                        context,
                        "强制重连仍失败：请重启手机后重试（部分 ROM 限制），" +
                            "或检查系统自启动/后台限制",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        if (settings.notificationListening && !accessGranted) {
            Toast.makeText(
                context,
                "通知使用权未开启，请到系统页面开启「寄意」的通知使用权",
                Toast.LENGTH_LONG,
            ).show()
        } else if (settings.notificationListening && !settings.listenerConnected) {
            Toast.makeText(
                context,
                "监听未连接：可点下方「强制重连监听」按钮，必要时重启手机",
                Toast.LENGTH_LONG,
            ).show()
        }
        if (settings.quickRecordEnabled && Settings.canDrawOverlays(context)) {
            context.startService(Intent(context, QuickNoteService::class.java))
        } else if (!settings.quickRecordEnabled) {
            context.stopService(Intent(context, QuickNoteService::class.java))
        }
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        ScreenHeader(
            title = "系统设置",
            description = "通知监听、快捷记忆与关于",
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ── 通用 ──
        SettingCategorySection(
            title = "通用",
            isExpanded = expandedSections["general"] == true,
            onToggle = { expandedSections["general"] = expandedSections["general"] != true },
        ) {
            SettingSwitchItem(
                title = "随时记",
                checked = settings.quickRecordEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Settings.canDrawOverlays(context)) {
                            repository.updateSetting("quickRecordEnabled", true)
                            context.startService(Intent(context, QuickNoteService::class.java))
                        } else {
                            pendingQuickRecord = true
                            repository.updateSetting("quickRecordEnabled", false)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                            Log.d(TAG, "悬浮窗权限未授予，已跳转系统授权页")
                        }
                    } else {
                        pendingQuickRecord = false
                        repository.updateSetting("quickRecordEnabled", false)
                        context.stopService(Intent(context, QuickNoteService::class.java))
                    }
                },
            )
            SettingClickItemWithIcon(
                icon = Icons.Outlined.FileDownload,
                title = "数据备份",
                subtitle = "导出全部记录与媒体到 下载/寄意备份",
                onClick = {
                    scope.launch {
                        val result = repository.exportAll()
                        val msg = when {
                            result == null -> "导出失败，请重试"
                            result.fileCount == 0 -> "当前应用数据目录为空，没有可导出的数据"
                            else -> "已导出 ${result.fileCount} 个文件到 下载/寄意备份/${result.fileName}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
            )
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "?"
            }
            SettingTextItem(title = "关于", value = "寄意 v$versionName")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 监听 ──
        SettingCategorySection(
            title = "监听",
            isExpanded = expandedSections["listen"] == true,
            onToggle = { expandedSections["listen"] = expandedSections["listen"] != true },
        ) {
            SettingSwitchItem(
                title = "通知监听",
                checked = settings.notificationListening,
                onCheckedChange = { enabled ->
                    Log.d(TAG, "通知监听开关 -> $enabled（当前授权=${isNotificationAccessGranted(context)}）")
                    if (enabled) {
                        if (isNotificationAccessGranted(context)) {
                            repository.updateSetting("notificationListening", true)
                        } else {
                            pendingNotification = true
                            repository.updateSetting("notificationListening", false)
                            context.startActivity(
                                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            )
                            Log.d(TAG, "通知使用权未授予，已跳转系统授权页")
                        }
                        requestAppListPermission()
                    } else {
                        pendingNotification = false
                        repository.updateSetting("notificationListening", false)
                        repository.updateSetting("listenerConnected", false)
                    }
                },
            )
            if (accessGranted) {
                if (settings.listenerConnected) {
                    SettingTextItem(title = "监听状态", value = "监听正常")
                } else {
                    SettingTextItemWithButton(
                        title = "监听状态",
                        value = "监听未连接，可强制重连",
                        buttonText = "强制重连",
                        onClick = {
                            AppNotificationListener.forceRebind(context)
                            Toast.makeText(context, "已触发强制重连（禁用再启用+重绑），请稍候…", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            val retentionSteps = listOf(1, 7, 10, 14, 21, 30)
            SettingSliderWithTextFieldItem(
                title = "临时记录保留时间",
                value = settings.starredRetentionDays,
                valueRange = 1..30,
                onValueChange = { raw ->
                    val snapped = retentionSteps.minBy { kotlin.math.abs(it - raw) }
                    repository.updateSettings {
                        it.copy(starredRetentionDays = snapped)
                    }
                },
            )
            SettingClickItem(
                title = "监听应用",
                value = if (settings.monitoredPackages.isEmpty()) {
                    "未选择应用"
                } else {
                    "已选择 ${settings.monitoredPackages.size} 个应用"
                },
                onClick = {
                    overlayHost.open {
                        MonitoredAppsPopup(
                            monitoredPackages = settings.monitoredPackages,
                            onToggle = { repository.toggleMonitoredPackage(it) },
                            onClose = { overlayHost.close() },
                        )
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 个性化 ──
        SettingCategorySection(
            title = "个性化",
            isExpanded = expandedSections["personal"] == true,
            onToggle = { expandedSections["personal"] = expandedSections["personal"] != true },
        ) {
            SettingClickItem(
                title = "主题色",
                value = "${if (settings.themeMode == "dark") "深色暗灰" else "浅色"} · ${themePresetName(settings.themePreset)}",
                onClick = {
                    overlayHost.open {
                        ThemePickerPopup(
                            current = settings.themePreset,
                            mode = settings.themeMode,
                            onSelect = { repository.updateThemePreset(it) },
                            onModeChange = { repository.updateThemeMode(it) },
                            onClose = { overlayHost.close() },
                        )
                    }
                },
            )
        }
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ) ?: return false
    return flat.split(":").any { it.contains(context.packageName) }
}

private const val TAG = "寄意设置"