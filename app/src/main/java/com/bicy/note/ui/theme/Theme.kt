package com.bicy.note.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bicy.note.data.LocalRepository

/**
 * 应用主题：从设置读取主题色预设与主题模式（themePreset / themeMode）。
 * 主题模式：light = 浅色（默认），dark = 深色暗灰。
 */
@Composable
fun 寄意Theme(content: @Composable () -> Unit) {
    val repository = LocalRepository.current
    val settings by repository.settings.collectAsStateWithLifecycle()
    val preset = remember(settings.themePreset) { themePresetById(settings.themePreset) }

    MaterialTheme(
        colorScheme = if (settings.themeMode == "dark") preset.darkScheme() else preset.lightScheme(),
        typography = Typography,
        content = content,
    )
}