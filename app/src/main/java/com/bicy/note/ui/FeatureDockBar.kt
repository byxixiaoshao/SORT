package com.bicy.note.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalPostOffice
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bicy.note.ui.screens.settings.EmailWindow
import com.bicy.note.ui.screens.settings.QuickNoteWindow
import com.bicy.note.ui.screens.settings.ScheduleWindow
import com.bicy.note.ui.screens.settings.SearchWindow

val DockBarHeight = 40.dp

/** 窗口从上「弹」下来：弹簧阻尼 < 1，先冲过目标再回落。 */
fun dockPopEnter(): androidx.compose.animation.EnterTransition =
    fadeIn(tween(120)) + slideInVertically(
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialOffsetY = { -it },
    )

/** 窗口被「拉」回上方：快速向上滑出。 */
fun dockPullExit(): androidx.compose.animation.ExitTransition =
    slideOutVertically(
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        targetOffsetY = { -it },
    ) + fadeOut(tween(160))

/**
 * 内阴影：在内容之上叠加四周向内渐变的半透明层，模拟按钮被按下的凹陷感。
 * 阴影按 [cornerRadius] 圆角裁剪，四角不留方形痕迹。
 */
private fun Modifier.insetShadow(
    color: Color = Color.Black.copy(alpha = 0.30f),
    depth: Dp = 6.dp,
    cornerRadius: Dp = 14.dp,
): Modifier = drawWithContent {
    drawContent()
    val r = depth.toPx()
    val top = Brush.verticalGradient(0f to color, (r / size.height).toFloat() to Color.Transparent)
    val bottom = Brush.verticalGradient((1f - r / size.height).toFloat() to Color.Transparent, 1f to color)
    val left = Brush.horizontalGradient(0f to color, (r / size.width).toFloat() to Color.Transparent)
    val right = Brush.horizontalGradient((1f - r / size.width).toFloat() to Color.Transparent, 1f to color)
    val path = Path().apply {
        addRoundRect(
            roundRect = RoundRect(
                rect = Rect(Offset.Zero, size),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
            ),
        )
    }
    val canvas = drawContext.canvas
    canvas.save()
    canvas.clipPath(path)
    drawRect(top)
    drawRect(bottom)
    drawRect(left)
    drawRect(right)
    canvas.restore()
}

private data class DockButton(
    val label: String,
    val icon: ImageVector,
    val window: @Composable () -> Unit,
)

private val dockButtons = listOf(
    DockButton("随写", Icons.Outlined.EditNote) { QuickNoteWindow() },
    DockButton("日程表", Icons.AutoMirrored.Outlined.EventNote) { ScheduleWindow() },
    DockButton("邮箱", Icons.Outlined.LocalPostOffice) { EmailWindow() },
    DockButton("全局搜索", Icons.Outlined.Search) { SearchWindow() },
)

/**
 * 顶部功能栏：一条长胶囊，内含四个按钮，按钮始终停留在胶囊内。
 *
 * 窗口动画：
 * - 弹出：窗口从顶部上方「弹」下来（弹簧阻尼 < 1，先冲过再回落），
 * - 切换：新窗口弹下的同时，旧窗口被「拉」回上方（快速向上滑出），
 * - 关闭：窗口同样被拉回上方。
 */
@Composable
fun FeatureDockBar(
    expandedIndex: Int?,
    onToggle: (Int?) -> Unit,
    screenWidth: Dp,
    screenHeight: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val windowWidth = minOf(screenWidth - 16.dp, 420.dp)
        val windowHeight = screenHeight * 0.72f
        var quickClearSignal by remember { mutableStateOf(0) }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topInset)
                .padding(horizontal = 10.dp)
                .height(DockBarHeight)
                .zIndex(1f)
                .clip(RoundedCornerShape(DockBarHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dockButtons.forEachIndexed { index, button ->
                    val isExpanded = expandedIndex == index
                    DockCapsuleButton(
                        button = button,
                        isExpanded = isExpanded,
                        onClick = { onToggle(if (isExpanded) null else index) },
                    )
                }
            }
        }

        dockButtons.forEachIndexed { index, _ ->
            AnimatedVisibility(
                visible = expandedIndex == index,
                enter = dockPopEnter(),
                exit = dockPullExit(),
                modifier = Modifier
                    .offset(
                        x = (screenWidth - windowWidth) / 2,
                        y = (screenHeight - windowHeight) / 2 + DockBarHeight,
                    )
                    .width(windowWidth)
                    .height(windowHeight)
                    .zIndex(2f),
            ) {
                if (index == 0) {
                    // 随写窗口：标题栏提供清空草稿按钮（× 左侧）
                    DockWindow(
                        button = dockButtons[index],
                        onClose = { onToggle(null) },
                        headerActions = {
                            IconButton(onClick = { quickClearSignal++ }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "清空草稿",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    ) {
                        QuickNoteWindow(
                            clearSignal = quickClearSignal,
                            onClearConsumed = { quickClearSignal = 0 },
                        )
                    }
                } else {
                    DockWindow(
                        button = dockButtons[index],
                        onClose = { onToggle(null) },
                    ) {
                        dockButtons[index].window()
                    }
                }
            }
        }
    }
}

@Composable
private fun DockCapsuleButton(
    button: DockButton,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                color = if (isExpanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .then(if (isExpanded) Modifier.insetShadow() else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = button.icon,
            contentDescription = button.label,
            tint = if (isExpanded) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = button.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isExpanded) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 窗口：含标题栏（图标/标题/可选操作按钮）与关闭按钮，窗口内部点击不会触发收起。
 */
@Composable
private fun DockWindow(
    button: DockButton,
    onClose: () -> Unit,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape,
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = button.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                headerActions?.invoke(this)
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                content()
            }
        }
    }
}