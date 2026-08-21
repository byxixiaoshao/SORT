package com.bicy.note.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Calendar("calendar", "日历", Icons.Outlined.CalendarMonth),
    Clock("clock", "时钟", Icons.Outlined.AccessTime),
    Settings("settings", "设置", Icons.Outlined.Settings),
}