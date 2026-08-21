package com.bicy.note.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.NoteDraft
import com.bicy.note.ui.ComposeDraft
import com.bicy.note.ui.components.markerIcon
import com.bicy.note.ui.components.markerOptions
import com.bicy.note.util.WavAudioRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private const val TAG = "寄意随写"

/**
 * 记录编辑区，分三个分区：
 * - 文本：多行输入
 * - 图片与视频：相册多选 / 拍照 / 拍视频，缩略图网格展示（showMedia = false 时隐藏）
 * - 音频：选择音频文件 / wav 录音（录音中加号按钮变为带计时的停止按钮）
 * 保存后写入目标日期 notes/text 文件，媒体复制到对应目录。
 *
 * 草稿机制（notes/drafts/）：选中的媒体立即复制/移入草稿目录，内容每次变化防抖落盘；
 * 编辑中断（关面板/杀进程）后再次进入自动恢复上次草稿。保存成功或点「清空草稿」时清除。
 * draft 为内存草稿（悬浮窗跨面板收起保留）；recorder 由外部提供（悬浮窗挂服务上）。
 */
@Composable
fun QuickNoteComposer(
    date: LocalDate,
    onSaved: () -> Unit = {},
    draft: ComposeDraft? = null,
    recorder: WavAudioRecorder? = null,
    showMedia: Boolean = true,
    clearSignal: Int = 0,
    onClearConsumed: () -> Unit = {},
    marker: String = MARKER_CIRCLE,
    onMarkerChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val activeRecorder = recorder ?: remember { WavAudioRecorder() }

    var text by remember { mutableStateOf(draft?.text ?: "") }
    var imageUris by remember { mutableStateOf(draft?.imageUris ?: emptyList()) }
    var videoUris by remember { mutableStateOf(draft?.videoUris ?: emptyList()) }
    var audioUri by remember { mutableStateOf(draft?.audioUri) }
    var recordedFile by remember { mutableStateOf(draft?.recordedFile) }
    var recording by remember { mutableStateOf(draft?.recording ?: false) }
    var recordSeconds by remember { mutableStateOf(draft?.recordSeconds ?: 0) }
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    var mediaMenuOpen by remember { mutableStateOf(false) }
    var audioMenuOpen by remember { mutableStateOf(false) }

    fun syncDraft() {
        draft?.let {
            it.text = text
            it.imageUris = imageUris
            it.videoUris = videoUris
            it.audioUri = audioUri
            it.recordedFile = recordedFile
            it.recording = recording
            it.recordSeconds = recordSeconds
        }
    }

    /** 当前内容 → 磁盘草稿（仅持久化已落入草稿目录的文件）。 */
    fun draftToNoteDraft(): NoteDraft = NoteDraft(
        text = text,
        images = imageUris.mapNotNull { repository.draftName(it) },
        videos = videoUris.mapNotNull { repository.draftName(it) },
        selectedAudio = audioUri?.let { repository.draftName(it) },
        recordedAudio = recordedFile?.let { repository.draftName(Uri.fromFile(it)) },
    )

    // 首次进入编辑：内存草稿为空时，从磁盘恢复上次中断的草稿
    LaunchedEffect(Unit) {
        val memoryEmpty = draft == null || (draft.text.isBlank() && draft.imageUris.isEmpty() &&
            draft.videoUris.isEmpty() && draft.audioUri == null && draft.recordedFile == null)
        if (!memoryEmpty) return@LaunchedEffect
        val disk = repository.loadDraft()
        if (disk.isEmpty) return@LaunchedEffect
        text = disk.text
        imageUris = disk.images.map { repository.draftUri(it) }
        videoUris = disk.videos.map { repository.draftUri(it) }
        audioUri = disk.selectedAudio?.let { repository.draftUri(it) }
        recordedFile = disk.recordedAudio?.let { repository.draftFile(it) }
        syncDraft()
        Log.d(
            TAG,
            "已从草稿恢复: 文本=${disk.text.length}字 图片=${disk.images.size} " +
                "视频=${disk.videos.size} 音频=${disk.selectedAudio != null || disk.recordedAudio != null}",
        )
    }

    // 内容变化后防抖保存草稿（仅保存已落入草稿目录的文件，录音中/未导入的不参与）
    LaunchedEffect(text, imageUris, videoUris, audioUri, recordedFile) {
        val hasContent = text.isNotBlank() || imageUris.isNotEmpty() || videoUris.isNotEmpty() ||
            audioUri != null || recordedFile != null
        if (!hasContent) return@LaunchedEffect
        delay(400)
        repository.saveDraft(draftToNoteDraft())
    }

    // 组件销毁（关面板/关窗口）时立即落盘，防抖窗口内关闭不丢草稿
    DisposableEffect(Unit) {
        onDispose {
            if (text.isNotBlank() || imageUris.isNotEmpty() || videoUris.isNotEmpty() ||
                audioUri != null || recordedFile != null
            ) {
                repository.saveDraft(draftToNoteDraft())
            }
        }
    }

    /** 拍照/拍视频产物（FileProvider captures 目录）移入草稿目录，返回草稿 Uri。 */
    fun moveCaptureToDraft(capUri: Uri): Uri? {
        val segment = capUri.lastPathSegment?.substringAfterLast('/') ?: return null
        val file = File(File(context.filesDir, "captures"), segment)
        if (!file.exists()) return null
        return repository.moveFileToDraft(file)?.let { Uri.fromFile(it) }
    }

    /** 停止录音并补写 WAV 头，再把录音文件移入草稿目录。 */
    fun stopRecording() {
        runCatching { activeRecorder.stop() }
            .onFailure { Log.e(TAG, "停止录音失败", it) }
        val file = recordedFile
        if (file != null && file.exists()) {
            WavAudioRecorder.writeWavHeader(file, activeRecorder.dataBytes)
        }
        recording = false
        recordedFile = file?.let { repository.moveFileToDraft(it) ?: it }
        Log.d(TAG, "录音停止${if (recordedFile != null) "，已移入草稿" else ""}")
    }

    fun clearDraft() {
        stopRecording()
        repository.clearDraft()
        text = ""
        imageUris = emptyList()
        videoUris = emptyList()
        audioUri = null
        recordedFile = null
        recordSeconds = 0
        draft?.clear()
        Log.d(TAG, "草稿已清空")
    }

    // 宿主（标题栏垃圾桶）触发清空草稿；清空后复位信号，避免下次进入重复清空
    LaunchedEffect(clearSignal) {
        if (clearSignal > 0) {
            clearDraft()
            onClearConsumed()
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            recordSeconds++
            syncDraft()
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        uris.forEach { uri ->
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            repository.importToDraftAsync(uri) { imported ->
                if (imported != null) {
                    if (isVideo) {
                        videoUris = videoUris + imported
                    } else {
                        imageUris = imageUris + imported
                    }
                } else {
                    Log.w(TAG, "媒体导入草稿失败: $uri")
                }
                syncDraft()
                repository.saveDraft(draftToNoteDraft())
            }
        }
    }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val cap = pendingCapture
        pendingCapture = null
        Log.d(TAG, "拍照结果: success=$success uri=$cap")
        if (success && cap != null) {
            moveCaptureToDraft(cap)?.let { imageUris = imageUris + it }
            syncDraft()
            repository.saveDraft(draftToNoteDraft())
        }
    }
    val captureVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        val cap = pendingCapture
        pendingCapture = null
        Log.d(TAG, "拍视频结果: success=$success uri=$cap")
        if (success && cap != null) {
            moveCaptureToDraft(cap)?.let { videoUris = videoUris + it }
            syncDraft()
            repository.saveDraft(draftToNoteDraft())
        }
    }
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            repository.importToDraftAsync(uri) { imported ->
                audioUri = imported ?: uri
                syncDraft()
                repository.saveDraft(draftToNoteDraft())
            }
        }
    }

    fun newCaptureUri(): Uri {
        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider",
            File(dir, "capture_${System.currentTimeMillis()}.jpg"),
        )
    }

    fun toggleRecording() {
        if (recording) {
            stopRecording()
        } else {
            recordSeconds = 0
            val file = File(context.filesDir, "recordings/rec_${System.currentTimeMillis()}.wav")
            try {
                activeRecorder.start(file)
                recordedFile = file
                recording = true
                Log.d(TAG, "录音开始: $file")
            } catch (e: Exception) {
                Log.e(TAG, "启动录音失败", e)
            }
        }
        syncDraft()
    }

    fun deleteRecording() {
        recordedFile?.let { activeRecorder.cancel(it) }
        recordedFile = null
        recordSeconds = 0
        syncDraft()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleRecording()
    }

    fun requestRecordOrToggle() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            toggleRecording()
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun save() {
        scope.launch {
            if (recording) {
                stopRecording()
                Log.d(TAG, "保存前已终止录音")
            }
            repository.addNote(
                date = date,
                text = text,
                imageUris = imageUris,
                videoUris = videoUris,
                audioUris = audioUri?.let { listOf(it) } ?: emptyList(),
                recordedAudio = recordedFile,
                marker = marker,
            )
            repository.clearDraft()
            text = ""
            imageUris = emptyList()
            videoUris = emptyList()
            audioUri = null
            recordedFile = null
            recordSeconds = 0
            draft?.clear()
            onSaved()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---------- 文本区 ----------
        SectionLabel("文本")
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                draft?.text = it
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "写点什么…") },
            minLines = 3,
        )

        if (showMedia) {
            HorizontalDivider()

            // ---------- 图片与视频区 ----------
            SectionLabel("图片与视频")
            if (imageUris.isNotEmpty() || videoUris.isNotEmpty()) {
                MediaGrid(
                    imageUris = imageUris,
                    videoUris = videoUris,
                    context = context,
                    onDeleteImage = { uri ->
                        repository.draftName(uri)?.let { repository.deleteDraftFile(it) }
                        imageUris = imageUris - uri
                    },
                    onDeleteVideo = { uri ->
                        repository.draftName(uri)?.let { repository.deleteDraftFile(it) }
                        videoUris = videoUris - uri
                    },
                )
            }
            Box {
                AddSquareButton(onClick = { mediaMenuOpen = true })
                DropdownMenu(
                    expanded = mediaMenuOpen,
                    onDismissRequest = { mediaMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "从相册选择") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                        },
                        onClick = {
                            mediaMenuOpen = false
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "拍照") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null)
                        },
                        onClick = {
                            mediaMenuOpen = false
                            pendingCapture = newCaptureUri()
                            pendingCapture?.let { takePicture.launch(it) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "拍视频") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Videocam, contentDescription = null)
                        },
                        onClick = {
                            mediaMenuOpen = false
                            pendingCapture = newCaptureUri()
                            pendingCapture?.let { captureVideo.launch(it) }
                        },
                    )
                }
            }
        }

        HorizontalDivider()

        // ---------- 音频区 ----------
        SectionLabel("音频")
        val audio = audioUri
        if (audio != null) {
            AudioRow(
                name = displayNameOf(context, audio),
                onDelete = {
                    audioUri?.let { uri -> repository.draftName(uri)?.let { repository.deleteDraftFile(it) } }
                    audioUri = null
                },
            )
        }
        if (recordedFile != null && !recording) {
            AudioRow(
                name = "已录制 ${formatSeconds(recordSeconds)}",
                onDelete = { deleteRecording() },
            )
        }
        Box {
            if (recording) {
                RecordingButton(
                    seconds = recordSeconds,
                    onClick = { toggleRecording() },
                )
            } else {
                AddSquareButton(onClick = { audioMenuOpen = true })
                DropdownMenu(
                    expanded = audioMenuOpen,
                    onDismissRequest = { audioMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "选择音频文件") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.MusicNote, contentDescription = null)
                        },
                        onClick = {
                            audioMenuOpen = false
                            pickAudio.launch(arrayOf("audio/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "开始录音") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Mic, contentDescription = null)
                        },
                        onClick = {
                            audioMenuOpen = false
                            requestRecordOrToggle()
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { save() },
            enabled = text.isNotBlank() || imageUris.isNotEmpty() || videoUris.isNotEmpty() ||
                audioUri != null || recordedFile != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            Text(text = "保存到 ${date.monthValue}月${date.dayOfMonth}日")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 方形「+」大按钮：点击弹出操作菜单。 */
@Composable
private fun AddSquareButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "添加",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** 录音中的「+」按钮：变红并显示录制时长，点击即停止录音。 */
@Composable
private fun RecordingButton(seconds: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = "停止录音",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = formatSeconds(seconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** 已选图片/视频的缩略图网格（每行 4 个，右上角可删除）。 */
@Composable
private fun MediaGrid(
    imageUris: List<Uri>,
    videoUris: List<Uri>,
    context: Context,
    onDeleteImage: (Uri) -> Unit,
    onDeleteVideo: (Uri) -> Unit,
) {
    val items = imageUris.map { it to false } + videoUris.map { it to true }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { (uri, isVideo) ->
                    if (isVideo) {
                        ThumbBox(
                            content = {
                                Icon(
                                    imageVector = Icons.Outlined.Movie,
                                    contentDescription = "视频",
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            onDelete = { onDeleteVideo(uri) },
                        )
                    } else {
                        UriThumb(uri = uri, context = context, onDelete = { onDeleteImage(uri) })
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.size(56.dp))
                }
            }
        }
    }
}

@Composable
private fun ThumbBox(
    content: @Composable () -> Unit,
    onDelete: () -> Unit,
) {
    Box(modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            DeleteBadge(onDelete = onDelete)
        }
    }
}

@Composable
private fun UriThumb(uri: Uri, context: Context, onDelete: () -> Unit) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = maxOf(info.size.width, info.size.height) / 112f
                if (scale > 1f) decoder.setTargetSampleSize(scale.toInt())
            }
        } catch (_: Exception) {
            null
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Box(modifier = Modifier.size(56.dp)) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            )
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DeleteBadge(onDelete = onDelete)
            }
        }
    } else {
        ThumbBox(
            content = {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            onDelete = onDelete,
        )
    }
}

@Composable
private fun DeleteBadge(onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(18.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(18.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun AudioRow(name: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun displayNameOf(context: Context, uri: Uri): String {
    val name = context.contentResolver.query(
        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    return name ?: "音频文件"
}

private fun formatSeconds(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)