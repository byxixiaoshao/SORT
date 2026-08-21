package com.bicy.note.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bicy.note.share.SharePreviewDialog
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.NoteRepository
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.MARKER_HEART
import com.bicy.note.data.model.MARKER_STAR
import com.bicy.note.data.model.NoteEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val markerOptions = listOf(
    MARKER_STAR to "临时",
    MARKER_CIRCLE to "普通",
    MARKER_HEART to "收藏",
)

fun markerIcon(marker: String) = when (marker) {
    MARKER_STAR -> Icons.Filled.Star
    MARKER_HEART -> Icons.Filled.Favorite
    else -> Icons.Outlined.Circle
}

/**
 * 标记筛选段：星/圆/心 三个等宽按钮（横向均分可用宽度）。
 * 点亮态为主题色填充 + 反色内容，熄灭态为浅灰底，状态一目了然。
 */
@Composable
fun MarkerToggleSegments(
    activeMarkers: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        markerOptions.forEach { (value, label) ->
            val active = value in activeMarkers
            val shape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = shape,
                    )
                    .clickable { onToggle(value) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = markerIcon(value),
                    contentDescription = null,
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * 记录详情：文本 + 图片（点击全屏缩放查看）+ 视频（可播放）+ 音频（可播放）。
 * 覆盖全屏的半透明背板，卡片居中（与顶部弹出的日历弹窗在视觉上明显区分）。
 * 支持切换记录标记：星（临时）/ 圆（正常）/ 心（收藏）。
 * 支持编辑文本内容和复制到剪贴板。
 */
@Composable
fun NoteDetailSheet(
    entry: NoteEntry,
    date: LocalDate,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    var fullImage by remember { mutableStateOf<String?>(null) }
    var marker by remember(entry) { mutableStateOf(entry.effectiveMarker()) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(entry) { mutableStateOf(entry.text) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var shareVideoCovers by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val mediaMax = (maxHeight - 220.dp).coerceIn(200.dp, 480.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .align(Alignment.Center)
                    .heightIn(min = 300.dp, max = (maxHeight - 24.dp).coerceAtMost(640.dp))
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 第一行：日期时间 + 标记图标 + 标记切换
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "${date.format(monthDayFormatter)} ${entry.time}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (marker != MARKER_CIRCLE) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = markerIcon(marker),
                                contentDescription = when (marker) {
                                    MARKER_STAR -> "临时标记"
                                    else -> "收藏标记"
                                },
                                tint = if (marker == MARKER_STAR) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // 标记切换
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            markerOptions.forEach { (value, _) ->
                                val active = marker == value
                                val shape = RoundedCornerShape(8.dp)
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(26.dp)
                                        .clip(shape)
                                        .background(
                                            color = if (active) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = shape,
                                        )
                                        .clickable {
                                            if (marker != value) {
                                                marker = value
                                                repository.setMarker(date, entry, value)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = markerIcon(value),
                                        contentDescription = null,
                                        tint = if (active) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    // 第二行：编辑/复制/分享/关闭
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.text.isNotEmpty()) {
                            IconButton(onClick = {
                                if (isEditing) {
                                    repository.updateNote(date, entry, editText)
                                    isEditing = false
                                } else {
                                    isEditing = true
                                }
                            }) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Outlined.Save else Icons.Outlined.Edit,
                                    contentDescription = if (isEditing) "保存" else "编辑",
                                    tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("记录内容", entry.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "复制",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = {
                                // 加载图片并打开分享预览弹窗
                                scope.launch(Dispatchers.IO) {
                                    val imgs = entry.images.mapNotNull { name ->
                                        com.bicy.note.share.CanvasRenderer.loadBitmap(context, "notes/image_and_video", name, maxSize = 2400)
                                    }
                                    val covers = entry.videos.mapNotNull { name ->
                                        com.bicy.note.share.CanvasRenderer.loadBitmap(context, "notes/image_and_video", name, maxSize = 2400)
                                    }
                                    shareImages = imgs
                                    shareVideoCovers = covers
                                    showShareDialog = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "分享",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "关闭")
                        }
                    }
                    // 正文
                    if (entry.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isEditing) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = entry.text,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(min = 160.dp, max = mediaMax)
                            .wrapContentHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        entry.images.forEach { name ->
                            LargeImageItem(
                                name = name,
                                context = context,
                                onClick = { fullImage = name },
                            )
                        }
                        entry.videos.forEach { name ->
                            VideoPlayerItem(name = name, context = context)
                        }
                        entry.audios.forEach { name ->
                            AudioPlayerItem(name = name, context = context)
                        }
                        if (entry.images.isEmpty() && entry.videos.isEmpty() && entry.audios.isEmpty()) {
                            Text(
                                text = "（无媒体内容）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    val full = fullImage
    if (full != null) {
        FullImageDialog(name = full, context = context, onDismiss = { fullImage = null })
    }

    if (showShareDialog) {
        SharePreviewDialog(
            date = date,
            entry = entry,
            images = shareImages,
            videoCovers = shareVideoCovers,
            onDismiss = { showShareDialog = false },
        )
    }
}

@Composable
private fun LargeImageItem(
    name: String,
    context: Context,
    onClick: () -> Unit,
) {
    var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) {
        bitmap = loadImage(context, NoteRepository.DIR_MEDIA, name, maxSize = 1400)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "图片加载失败",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FullImageDialog(
    name: String,
    context: Context,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(name) {
                bitmap = loadImage(context, NoteRepository.DIR_MEDIA, name, maxSize = 2600)
            }
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                offset = (offset + pan) * (newScale / scale)
                                scale = newScale
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "图片加载失败", color = Color.White)
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerItem(name: String, context: Context) {
    val file = remember(name) { File(context.filesDir, "${NoteRepository.DIR_MEDIA}/$name") }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.fromFile(file))
                    setMediaController(MediaController(ctx))
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AudioPlayerItem(name: String, context: Context) {
    val file = remember(name) { File(context.filesDir, "${NoteRepository.DIR_AUDIO}/$name") }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    val player = remember {
        try {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                durationMs = duration
                setOnCompletionListener {
                    isPlaying = false
                    progress = 0f
                }
            }
        } catch (_: Exception) {
            failed = true
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let {
                progress = it.currentPosition.toFloat()
                delay(300)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                val p = player ?: return@IconButton
                if (isPlaying) {
                    p.pause()
                    isPlaying = false
                } else {
                    p.seekTo(progress.toInt())
                    p.start()
                    isPlaying = true
                }
            },
            enabled = player != null,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = progress,
                onValueChange = {
                    progress = it
                    player?.seekTo(it.toInt())
                },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                enabled = player != null,
            )
            Text(
                text = if (failed) "无法播放此音频" else
                    "${formatMs(progress.toInt())} / ${formatMs(durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = name.substringAfterLast('/'),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun loadImage(context: Context, dir: String, name: String, maxSize: Int): Bitmap? {
    return try {
        val file = File(context.filesDir, "$dir/$name")
        if (!file.exists()) return null
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = maxOf(info.size.width, info.size.height) / maxSize.toFloat()
            if (scale > 1f) decoder.setTargetSampleSize(scale.toInt())
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatMs(ms: Int): String {
    val total = ms / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")