package com.bicy.note.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ShadowConfig(
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val blurRadius: Dp = 8.dp,
    val spreadRadius: Dp = 0.dp,
    val color: Color = Color.Black.copy(alpha = 0.25f),
) {
    companion object {
        val Light = ShadowConfig(
            offsetY = 2.dp,
            blurRadius = 4.dp,
            spreadRadius = 0.dp,
            color = Color.Black.copy(alpha = 0.18f),
        )
    }
}

fun Modifier.dropShadow(
    config: ShadowConfig = ShadowConfig.Light,
    shape: Shape = RoundedCornerShape(0.dp),
    clip: Boolean = true,
): Modifier = this.drawBehind {
    if (size.width.isNaN() || size.height.isNaN() || size.width <= 0f || size.height <= 0f) {
        return@drawBehind
    }

    val spreadPx = config.spreadRadius.toPx()
    val blurPx = config.blurRadius.toPx()

    drawIntoCanvas { canvas ->
        val nativePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(
                blurPx,
                config.offsetX.toPx(),
                config.offsetY.toPx(),
                config.color.toArgb(),
            )
        }

        val outline = shape.createOutline(size, layoutDirection, this)

        when (outline) {
            is Outline.Rectangle -> {
                canvas.nativeCanvas.drawRect(
                    -spreadPx,
                    -spreadPx,
                    size.width + spreadPx,
                    size.height + spreadPx,
                    nativePaint,
                )
            }
            is Outline.Rounded -> {
                val radiusX = outline.roundRect.topLeftCornerRadius.x
                val radiusY = outline.roundRect.topLeftCornerRadius.y
                canvas.nativeCanvas.drawRoundRect(
                    -spreadPx,
                    -spreadPx,
                    size.width + spreadPx,
                    size.height + spreadPx,
                    radiusX,
                    radiusY,
                    nativePaint,
                )
            }
            is Outline.Generic -> {
            }
        }
    }
}.then(if (clip) Modifier.clip(shape) else Modifier)

/** 分组折叠卡片：标题行（点击展开/收起）+ 组内单项卡片列表 */
@Composable
fun SettingCategorySection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isExpanded) "收起" else "展开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content()
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** 开关单项卡片 */
@Composable
fun SettingSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
        )
    }
}

/** 点击单项卡片：左侧标题，右侧当前值 */
@Composable
fun SettingClickItem(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** 带图标和说明文字的点击单项卡片：左侧图标 + 标题/副标题，右侧辅助值或 "›" */
@Composable
fun SettingClickItemWithIcon(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .run { if (subtitle.isNullOrBlank()) height(56.dp) else this }
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = if (subtitle.isNullOrBlank()) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

/** 静态展示单项卡片 */
@Composable
fun SettingTextItem(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** 标题 + 说明 + 操作按钮单项卡片 */
@Composable
fun SettingTextItemWithButton(
    title: String,
    value: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = buttonText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 滑条 + 输入框单项卡片 */
@Composable
fun SettingSliderWithTextFieldItem(
    title: String,
    value: Int,
    valueRange: ClosedRange<Int>,
    onValueChange: (Int) -> Unit,
) {
    var editText by remember { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (editText != value.toString()) {
            editText = value.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = editText,
                onValueChange = { newValue ->
                    editText = newValue
                    val parsed = newValue.toIntOrNull()
                    if (parsed != null && parsed in valueRange) {
                        onValueChange(parsed)
                    }
                },
                modifier = Modifier.width(100.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        Slider(
            value = value.toFloat().coerceIn(
                valueRange.start.toFloat(),
                valueRange.endInclusive.toFloat(),
            ),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.start.toFloat()..valueRange.endInclusive.toFloat(),
            steps = (valueRange.endInclusive - valueRange.start - 1).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}