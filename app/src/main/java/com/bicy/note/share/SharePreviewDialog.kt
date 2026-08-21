package com.bicy.note.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.NoteEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharePreviewDialog(
    date: LocalDate,
    entry: NoteEntry,
    images: List<Bitmap>,
    videoCovers: List<Bitmap>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTemplate by remember { mutableStateOf(BuiltinTemplates.minimal) }
    var bgColor by remember { mutableStateOf("#FAFAFA") }
    var textColor by remember { mutableStateOf("#1A1A1A") }
    var showWatermark by remember { mutableStateOf(true) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 预览时实时渲染
    val time = try { LocalTime.parse(entry.time) } catch (_: Exception) { LocalTime.NOON }
    val dateTime = LocalDateTime.of(date, time)

    // 预设背景色
    val bgPresets = listOf(
        "#FAFAFA" to "浅灰",
        "#FFFFFF" to "纯白",
        "#F5F5DC" to "米色",
        "#1A1A1A" to "深黑",
        "#263238" to "深蓝灰",
        "#FFF8E1" to "暖黄",
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "分享预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 预览图
            val preview = renderPreview(
                schema = selectedTemplate,
                dateTime = dateTime,
                entry = entry,
                images = images,
                videoCovers = videoCovers,
                bgColor = bgColor,
                textColor = textColor,
                showWatermark = showWatermark,
            )
            previewBitmap = preview

            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "预览",
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(preview.width.toFloat() / preview.height)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp),
                        ),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 模板选择
            Text(
                text = "模板",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BuiltinTemplates.all.forEach { template ->
                    val selected = selectedTemplate.id == template.id
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { selectedTemplate = template },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = template.name,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 背景色选择
            Text(
                text = "背景色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bgPresets.forEach { (hex, label) ->
                    val selected = bgColor == hex
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(android.graphics.Color.parseColor(hex).let {
                                androidx.compose.ui.graphics.Color(
                                    red = (it shr 16 and 0xFF) / 255f,
                                    green = (it shr 8 and 0xFF) / 255f,
                                    blue = (it and 0xFF) / 255f,
                                )
                            })
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            .clickable { bgColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text(
                                text = "✓",
                                color = if (hex in listOf("#1A1A1A", "#263238")) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 文字颜色
            Text(
                text = "文字颜色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("#1A1A1A" to "黑", "#FFFFFF" to "白", "#5D4037" to "棕", "#1565C0" to "蓝").forEach { (hex, label) ->
                    val selected = textColor == hex
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(android.graphics.Color.parseColor(hex).let {
                                androidx.compose.ui.graphics.Color(
                                    red = (it shr 16 and 0xFF) / 255f,
                                    green = (it shr 8 and 0xFF) / 255f,
                                    blue = (it and 0xFF) / 255f,
                                )
                            })
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            .clickable { textColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text(
                                text = "✓",
                                color = if (hex == "#1A1A1A") MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 水印开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "显示水印",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Switch(
                    checked = showWatermark,
                    onCheckedChange = { showWatermark = it },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 分享按钮
            TextButton(
                onClick = {
                    previewBitmap?.let { bitmap ->
                        shareBitmap(context, bitmap)
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "分享图片")
            }
        }
    }
}

private fun renderPreview(
    schema: TemplateSchema,
    dateTime: LocalDateTime,
    entry: NoteEntry,
    images: List<Bitmap>,
    videoCovers: List<Bitmap>,
    bgColor: String,
    textColor: String,
    showWatermark: Boolean,
): Bitmap? {
    val bgLong = android.graphics.Color.parseColor(bgColor).toLong() and 0xFFFFFFFFL shl 0 or
        (0xFF000000L shl 24)
    val textLong = android.graphics.Color.parseColor(textColor).toLong() and 0xFFFFFFFFL shl 0 or
        (0xFF000000L shl 24)

    val customSchema = schema.copy(
        canvas = schema.canvas.copy(
            background = BackgroundConfig(type = "solid", color = bgLong),
        ),
        nodes = schema.nodes.map { node ->
            when (node) {
                is LayoutNode.TextNode -> node.copy(color = textLong)
                is LayoutNode.WatermarkNode -> node.copy(opacity = if (showWatermark) 0.5f else 0f)
                else -> node
            }
        },
    )

    return try {
        ShareImageBuilder.renderWithDateTime(
            context = null as android.content.Context?,
            schema = customSchema,
            dateTime = dateTime,
            entry = entry,
            images = images,
            videoCovers = videoCovers,
        )
    } catch (_: Exception) {
        null
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享记录图片"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
