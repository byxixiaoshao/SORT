package com.bicy.note.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bicy.note.ui.navigation.MainTab
import com.bicy.note.ui.screens.calendar.CalendarScreen
import com.bicy.note.ui.screens.calendar.DayDetailOverlay
import com.bicy.note.ui.screens.ClockScreen
import com.bicy.note.ui.screens.SettingsScreen
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 设置类弹窗宿主：设置页里的设置项产生的弹窗，应显示在所有内容之上，
 * 且弹窗下层内容高斯模糊 + 暗淡。任何设置项需要弹窗时调用 open。
 */
class SettingsOverlayHost(
    val open: (@Composable () -> Unit) -> Unit,
    val close: () -> Unit,
)

val LocalSettingsOverlayHost = androidx.compose.runtime.staticCompositionLocalOf<SettingsOverlayHost> {
    error("SettingsOverlayHost 未初始化")
}

/**
 * 层叠规则（自下而上）：
 * 0. 主页面 + 底部导航栏
 * 1. 非设置弹窗（日历详情、功能栏窗口）：在主页面与导航栏之上、功能栏之下
 * 2. 顶部功能栏
 * 3. 设置类弹窗（设置项产生）：在所有之上，下层高斯模糊 + 暗淡
 */
@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var expandedDock by remember { mutableStateOf<Int?>(null) }
    var calendarDate by remember { mutableStateOf<LocalDate?>(null) }
    var entryToView by remember { mutableStateOf<com.bicy.note.data.model.NoteEntry?>(null) }
    var settingsOverlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var settingsOverlayVisible by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp }
    val screenHeight = with(density) { configuration.screenHeightDp.dp }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    CompositionLocalProvider(
        LocalSettingsOverlayHost provides SettingsOverlayHost(
            open = {
                settingsOverlay = it
                settingsOverlayVisible = true
            },
            close = { settingsOverlayVisible = false },
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val overlayOpen = settingsOverlayVisible
            val dockOpen = expandedDock != null
            val calendarOpen = calendarDate != null
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (overlayOpen || dockOpen || calendarOpen) Modifier.blur(20.dp) else Modifier
                    ),
            ) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                                    label = { Text(text = tab.label) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = MainTab.Calendar.route,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topInset + DockBarHeight),
                        ) {
                            composable(MainTab.Calendar.route) {
                                CalendarScreen(onDateClick = { calendarDate = it })
                            }
                            composable(MainTab.Clock.route) { ClockScreen() }
                            composable(MainTab.Settings.route) { SettingsScreen() }
                        }
                    }
                }
            }

            DayDetailOverlay(
                visible = calendarDate != null,
                date = calendarDate ?: LocalDate.now(),
                onDismiss = { calendarDate = null },
                topOffset = topInset + DockBarHeight,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f),
                entryToView = entryToView,
                onEntryViewed = { entryToView = null },
                onOpenDate = { target, entry ->
                    calendarDate = target
                    entryToView = entry
                },
            )

            FeatureDockBar(
                expandedIndex = expandedDock,
                onToggle = { expandedDock = it },
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                topInset = topInset,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
            )

            AnimatedVisibility(
                visible = dockOpen,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1.5f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }

            AnimatedVisibility(
                visible = settingsOverlayVisible,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220)),
                modifier = Modifier.zIndex(3f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                    )
                    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                        settingsOverlay?.invoke()
                    }
                }
            }

            LaunchedEffect(settingsOverlayVisible) {
                if (!settingsOverlayVisible) {
                    delay(220)
                    settingsOverlay = null
                }
            }
        }
    }
}