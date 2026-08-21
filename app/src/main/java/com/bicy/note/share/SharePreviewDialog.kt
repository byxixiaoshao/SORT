package com.bicy.note.share

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bicy.note.data.model.NoteEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

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

    // 颜色用 HSL 滑块控制
    var bgHue by remember { mutableFloatStateOf(0f) }       // 0~360
    var bgSat by remember { mutableFloatStateOf(0f) }       // 0~1 (0=灰阶)
    var bgLit by remember { mutableFloatStateOf(0.96f) }    // 0~1
    var textHue by remember { mutableFloatStateOf(0f) }
    var textSat by remember { mutableFloatStateOf(0f) }
    var textLit by remember { mutableFloatStateOf(0.1f) }

    var showWatermark by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("来自「寄意」笔记") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showJsonEditor by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var jsonError by remember { mutableStateOf<String?>(null) }

    val time = try { LocalTime.parse(entry.time) } catch (_: Exception) { LocalTime.NOON }
    val dateTime = LocalDateTime.of(date, time)

    val bgColor = hslToArgb(bgHue, bgSat, bgLit)
    val textColor = hslToArgb(textHue, textSat, textLit)

    LaunchedEffect(selectedEngine, bgColor, textColor, showWatermark, watermarkText) {
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
                watermarkText = watermarkText,
            ),
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ═══════════ 上方：标题 + 预览 ═══════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("分享预览", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
            }
        }

        // 预览图（可滚动，适应高模板）
        val preview = previewBitmap
        if (preview != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "预览",
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .heightIn(max = 400.dp)
                        .aspectRatio(preview.width.toFloat() / preview.height)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // ═══════════ 中间：配置项（可滚动） ═══════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 模板选择
            Text("模板", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val builtinIds = remember { TemplateRegistry.getBuiltin().map { it.id }.toSet() }
                engines.forEach { engine ->
                    val selected = selectedEngine?.id == engine.id
                    val isBuiltin = engine.id in builtinIds
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isBuiltin) {
                                    Modifier.clickable { selectedEngine = engine }
                                } else {
                                    Modifier.combinedClickable(
                                        onClick = { selectedEngine = engine },
                                        onLongClick = {
                                            TemplateRegistry.unregister(engine.id)
                                            engines = TemplateRegistry.getAll()
                                            if (selectedEngine?.id == engine.id) {
                                                selectedEngine = engines.firstOrNull()
                                            }
                                        },
                                    )
                                }
                            )
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp),
                            ),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            engine.name,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { showJsonEditor = true },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text("+ 添加", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 背景色滑块
            Text("背景色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            ColorSlider(
                hue = bgHue, onHueChange = { bgHue = it },
                sat = bgSat, onSatChange = { bgSat = it },
                lit = bgLit, onLitChange = { bgLit = it },
                previewColor = Color.hsl(bgHue, bgSat, bgLit),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 文字色滑块
            Text("文字颜色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            ColorSlider(
                hue = textHue, onHueChange = { textHue = it },
                sat = textSat, onSatChange = { textSat = it },
                lit = textLit, onLitChange = { textLit = it },
                previewColor = Color.hsl(textHue, textSat, textLit),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 水印
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("水印", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = showWatermark, onCheckedChange = { showWatermark = it })
            }
            if (showWatermark) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("输入水印文字") },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ═══════════ 下方：分享按钮（固定） ═══════════
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("分享图片")
        }
    }

    // JSON 模板编辑器弹窗
    if (showJsonEditor) {
        JsonEditorOverlay(
            jsonInput = jsonInput,
            onJsonChange = { jsonInput = it; jsonError = null },
            jsonError = jsonError,
            onFillExample = { jsonInput = EXAMPLE_JSON.trimIndent() },
            onConfirm = {
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
            onDismiss = { showJsonEditor = false },
        )
    }
}

@Composable
private fun ColorSlider(
    hue: Float, onHueChange: (Float) -> Unit,
    sat: Float, onSatChange: (Float) -> Unit,
    lit: Float, onLitChange: (Float) -> Unit,
    previewColor: Color,
) {
    Column {
        // 色相滑块 0~360
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("色相", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
            Slider(
                value = hue,
                onValueChange = { onHueChange(it) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = previewColor,
                    activeTrackColor = Color.hsl(hue, 1f, 0.5f),
                ),
            )
            Text("${hue.roundToInt()}°", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
        }
        // 饱和度滑块 0~1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("饱和", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
            Slider(
                value = sat,
                onValueChange = { onSatChange(it) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = previewColor,
                    activeTrackColor = Color.hsl(hue, 1f, 0.5f),
                ),
            )
            Text("${(sat * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
        }
        // 明度滑块 0~1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("明度", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
            Slider(
                value = lit,
                onValueChange = { onLitChange(it) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = previewColor,
                    activeTrackColor = Color.hsl(hue, sat, 0.5f),
                ),
            )
            Text("${(lit * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
        }
        // 预览色块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(previewColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun JsonEditorOverlay(
    jsonInput: String,
    onJsonChange: (String) -> Unit,
    jsonError: String?,
    onFillExample: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("粘贴模板 JSON：", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = jsonInput,
                onValueChange = onJsonChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                placeholder = { Text("{ ... }", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                isError = jsonError != null,
                supportingText = jsonError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onFillExample, modifier = Modifier.weight(1f)) { Text("填入示例") }
                TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认添加") }
            }
        }
    }
}

/** HSL → ARGB Long */
private fun hslToArgb(h: Float, s: Float, l: Float): Long {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60 -> tripleOf(c, x, 0f)
        h < 120 -> tripleOf(x, c, 0f)
        h < 180 -> tripleOf(0f, c, x)
        h < 240 -> tripleOf(0f, x, c)
        h < 300 -> tripleOf(x, 0f, c)
        else -> tripleOf(c, 0f, x)
    }
    val ri = ((r + m) * 255).toInt().coerceIn(0, 255)
    val gi = ((g + m) * 255).toInt().coerceIn(0, 255)
    val bi = ((b + m) * 255).toInt().coerceIn(0, 255)
    return (0xFF.toLong() shl 24) or (ri.toLong() shl 16) or (gi.toLong() shl 8) or bi.toLong()
}

private fun tripleOf(a: Float, b: Float, c: Float) = Triple(a, b, c)

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
    { "type": "TextNode", "id": "text", "x": 8, "y": -1, "width": 84, "fontSize": 48, "color": 4278190080, "content": "{{text}}" },
    { "type": "ImageGridNode", "id": "img", "x": 8, "y": -1, "width": 84, "columns": 2, "spacing": 16, "borderRadius": 12, "maxImages": 6 },
    { "type": "WatermarkNode", "id": "wm", "x": 50, "y": -1, "fontSize": 24, "color": 3086721395, "text": "来自「寄意」笔记" }
  ],
  "decorations": [
    { "type": "BorderDecoration", "id": "b", "color": 4280825704, "thickness": 2, "cornerRadius": 20 }
  ]
}
""".trimIndent()
