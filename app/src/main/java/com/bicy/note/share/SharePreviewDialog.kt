package com.bicy.note.share

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bicy.note.data.model.NoteEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    var engines by remember { mutableStateOf(TemplateRegistry.getAll()) }
    var selectedEngine by remember { mutableStateOf(engines.firstOrNull()) }
    var bgColor by remember { mutableStateOf(0xFFFAFAFA) }
    var textColor by remember { mutableStateOf(0xFF1A1A1A) }
    var showWatermark by remember { mutableStateOf(true) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showJsonEditor by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var jsonError by remember { mutableStateOf<String?>(null) }

    val time = try { LocalTime.parse(entry.time) } catch (_: Exception) { LocalTime.NOON }
    val dateTime = LocalDateTime.of(date, time)

    // 预设背景色
    val bgPresets = listOf(
        0xFFFAFAFA to "浅灰",
        0xFFFFFFFF to "纯白",
        0xFFF5F5DC to "米色",
        0xFF1A1A1A to "深黑",
        0xFF263238 to "深蓝灰",
        0xFFFFF8E1 to "暖黄",
    )
    val textPresets = listOf(
        0xFF1A1A1A to "黑",
        0xFFFFFFFF to "白",
        0xFF5D4037 to "棕",
        0xFF1565C0 to "蓝",
    )

    // 实时预览渲染
    LaunchedEffect(selectedEngine, bgColor, textColor, showWatermark) {
        previewBitmap = selectedEngine?.render(
            context = context,
            dateTime = dateTime,
            entry = entry,
            images = images,
            videoCovers = videoCovers,
            config = TemplateConfig(
                bgColor = bgColor,
                textColor = textColor,
                showWatermark = showWatermark,
            ),
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("分享预览", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 预览图
            val preview = previewBitmap
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "预览",
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(preview.width.toFloat() / preview.height)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 模板选择
            Text("模板", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                engines.forEach { engine ->
                    val selected = selectedEngine?.id == engine.id
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { selectedEngine = engine },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            engine.name,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                // 添加自定义模板按钮
                Surface(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { showJsonEditor = true },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        "+ 添加",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 背景色
            Text("背景色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bgPresets.forEach { (hex, _) ->
                    val selected = bgColor == hex
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(android.graphics.Color.argb(
                                ((hex shr 24) and 0xFF).toInt(),
                                ((hex shr 16) and 0xFF).toInt(),
                                ((hex shr 8) and 0xFF).toInt(),
                                (hex and 0xFF).toInt(),
                            )))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            .clickable { bgColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = if (hex == 0xFF1A1A1A || hex == 0xFF263238) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 文字颜色
            Text("文字颜色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                textPresets.forEach { (hex, _) ->
                    val selected = textColor == hex
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(android.graphics.Color.argb(
                                ((hex shr 24) and 0xFF).toInt(),
                                ((hex shr 16) and 0xFF).toInt(),
                                ((hex shr 8) and 0xFF).toInt(),
                                (hex and 0xFF).toInt(),
                            )))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            .clickable { textColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = if (hex == 0xFF1A1A1A) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 水印
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("显示水印", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Switch(checked = showWatermark, onCheckedChange = { showWatermark = it })
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 分享按钮
            TextButton(
                onClick = {
                    previewBitmap?.let { bitmap ->
                        val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, uri)
                                type = "image/png"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "分享记录图片",
                        ))
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("分享图片")
            }
        }
    }

    // JSON 模板编辑器弹窗
    if (showJsonEditor) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                .clickable { showJsonEditor = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("添加自定义模板", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showJsonEditor = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "粘贴模板 JSON：",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it; jsonError = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    placeholder = { Text("{ ... }", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    isError = jsonError != null,
                    supportingText = jsonError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            jsonInput = EXAMPLE_JSON.trimIndent()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("填入示例") }

                    TextButton(
                        onClick = {
                            val template = JsonTemplate.fromJson(jsonInput)
                            if (template != null) {
                                TemplateRegistry.register(template)
                                engines = TemplateRegistry.getAll()
                                selectedEngine = template
                                showJsonEditor = false
                            } else {
                                jsonError = "JSON 解析失败，请检查格式"
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("确认添加") }
                }
            }
        }
    }
}

private val EXAMPLE_JSON = """
{
  "id": "user_custom",
  "name": "我的模板",
  "canvas": {
    "width": 1080,
    "height": 1920,
    "background": { "type": "solid", "color": 4294967295 }
  },
  "nodes": [
    { "type": "DateNode", "id": "date", "x": 8, "y": 5, "fontSize": 32, "color": 2301546496 },
    { "type": "TextNode", "id": "text", "x": 8, "y": 12, "width": 84, "fontSize": 48, "color": 4278190080, "content": "{{text}}" },
    { "type": "ImageGridNode", "id": "img", "x": 8, "y": 60, "width": 84, "columns": 2, "spacing": 16, "borderRadius": 12, "maxImages": 6 },
    { "type": "WatermarkNode", "id": "wm", "x": 50, "y": 95, "fontSize": 24, "color": 3086721395, "text": "来自「寄意」笔记" }
  ],
  "decorations": [
    { "type": "BorderDecoration", "id": "b", "color": 4280825704, "thickness": 2, "cornerRadius": 20 }
  ]
}
""".trimIndent()
