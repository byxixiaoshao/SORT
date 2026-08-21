package com.bicy.note.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bicy.note.ui.theme.ThemePresets
import com.bicy.note.ui.theme.themePresetById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置类弹窗的通用外壳：居中卡片 + 透明点击关闭层。
 * 由 AppNavigation 的 LocalSettingsOverlayHost 承载（顶层，下层高斯模糊 + 暗淡）。
 */
@Composable
fun SettingsPopupShell(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    onDismiss()
                },
        )
        Surface(
            modifier = modifier.align(Alignment.Center),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
        ) {
            content()
        }
    }
}

/**
 * 监听应用弹窗：搜索框 + 全部应用列表 + Switch 开关。
 * 启动时若没有应用列表权限则请求（QUERY_ALL_PACKAGES）。
 */
@Composable
fun MonitoredAppsPopup(
    monitoredPackages: List<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var loading by remember { mutableStateOf(true) }
    val loadScope = rememberCoroutineScope()

    /** 后台线程加载应用列表，避免主线程卡顿（首次打开弹窗等待几秒的根因）。 */
    fun loadApps() {
        loadScope.launch {
            val list = withContext(Dispatchers.IO) { loadInstalledApps(context) }
            apps = list
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadApps() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { loadApps() }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.QUERY_ALL_PACKAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.QUERY_ALL_PACKAGES)
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { (pkg, label) ->
                pkg.contains(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
            }
        }
    }

    SettingsPopupShell(
        onDismiss = onClose,
        modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.78f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "监听应用",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "共 ${apps.size} 个应用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "关闭")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(text = "搜索应用…") },
                singleLine = true,
            )
            HorizontalDivider()
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "正在加载应用列表…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "没有匹配的应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.first }) { (pkg, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Switch(
                                checked = pkg in monitoredPackages,
                                onCheckedChange = { onToggle(pkg) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                text = "选中的应用来通知时会写入当天记录（带星标），一天后自动移除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** 主题色选择弹窗：主题模式（浅色/深色暗灰）+ 预设列表 + 色块 + 当前选中标记。 */
@Composable
fun ThemePickerPopup(
    current: String,
    mode: String,
    onSelect: (String) -> Unit,
    onModeChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    SettingsPopupShell(
        onDismiss = onClose,
        modifier = Modifier.fillMaxWidth(0.92f),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主题色",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "选择主题模式与喜欢的主题色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "关闭")
                }
            }
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                ModeChip(
                    label = "浅色",
                    description = "整体明亮色调",
                    selected = mode != "dark",
                    onClick = { onModeChange("light") },
                    modifier = Modifier.weight(1f),
                )
                ModeChip(
                    label = "深色暗灰",
                    description = "暗灰背景 + 主题色强调",
                    selected = mode == "dark",
                    onClick = { onModeChange("dark") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "主题色",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ThemePresets.forEach { preset ->
                val selected = preset.id == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(preset.id)
                            onClose()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(preset.light.primary, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (selected) "当前使用" else "轻触切换",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "当前主题",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 读取已安装应用列表（包名 to 名称）。
 * 有 QUERY_ALL_PACKAGES 时返回全部应用；否则退回仅启动器应用。
 */
fun loadInstalledApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return try {
        pm.getInstalledApplications(0)
            .filterNot { it.packageName == context.packageName }
            .map { pkg ->
                val label = runCatching { pm.getApplicationLabel(pkg).toString() }
                    .getOrElse { pkg.packageName }
                pkg.packageName to label
            }
            .sortedBy { it.second }
    } catch (_: SecurityException) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pkg to pm.getApplicationLabel(info).toString()
                }.getOrNull()
            }
            .sortedBy { it.second }
    }
}

fun themePresetName(id: String): String = themePresetById(id).name