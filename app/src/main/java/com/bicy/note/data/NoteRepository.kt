package com.bicy.note.data

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.bicy.note.data.model.AppSettings
import com.bicy.note.data.model.DayNotes
import com.bicy.note.data.model.DndRule
import com.bicy.note.data.model.EmailRecord
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.MARKER_STAR
import com.bicy.note.data.model.NoteDraft
import com.bicy.note.data.model.NoteEntry
import com.bicy.note.data.model.ScheduleItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 数据目录结构（filesDir 下）：
 * - notes/text/yyyy_MM_dd_text.json        当日记录（按 time 排序）
 * - notes/image_and_video/yyyy_MM_dd_HH_mm_ss.ext  图片/视频
 * - notes/audio/yyyy_MM_dd_HH_mm_ss.wav    音频
 * - notes/drafts/draft.json + 媒体文件     随写草稿（编辑中断恢复）
 * - emails/emails.json
 * - schedules/schedules.json
 * - settings.json
 */
class NoteRepository private constructor(private val context: Context) {

    private val store = JsonStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 通知记录写入互斥：去重检查与写入必须原子，避免并发通知产生重复记录。 */
    private val noteWriteMutex = Mutex()

    private val _schedules = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val schedules: StateFlow<List<ScheduleItem>> = _schedules.asStateFlow()

    private val _emails = MutableStateFlow<List<EmailRecord>>(emptyList())
    val emails: StateFlow<List<EmailRecord>> = _emails.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _notesVersion = MutableStateFlow(0)
    val notesVersion: StateFlow<Int> = _notesVersion.asStateFlow()

    init {
        // 配置：启动时同步读一次，之后任何改动同步写一次，不搞异步与竞态
        _settings.value = store.readSync(
            FILE_SETTINGS, AppSettings.serializer(), AppSettings()
        )
        scope.launch {
            _schedules.value = store.read(
                FILE_SCHEDULES, ListSerializer(ScheduleItem.serializer()), emptyList()
            )
        }
        scope.launch {
            _emails.value = store.read(
                FILE_EMAILS, ListSerializer(EmailRecord.serializer()), emptyList()
            )
        }
        pruneStarredNotes()
    }

    // ---------- 记录（notes） ----------

    suspend fun getDayNotes(date: LocalDate): DayNotes =
        store.read(textFileName(date), DayNotes.serializer(), DayNotes())

    suspend fun saveDayNotes(date: LocalDate, notes: DayNotes) {
        store.writeSync(textFileName(date), DayNotes.serializer(), notes)
    }

    /** 加载全部有记录的日期 → 当日记录，供日历标记与统计。 */
    suspend fun loadAllNotes(): Map<LocalDate, DayNotes> = withContext(Dispatchers.IO) {
        val dir = store.file(DIR_TEXT)
        if (!dir.exists()) return@withContext emptyMap()
        dir.listFiles { f -> f.isFile && f.name.endsWith("_text.json") }
            ?.mapNotNull { file ->
                val date = parseDateFromFileName(file.name) ?: return@mapNotNull null
                try {
                    date to store.read("$DIR_TEXT/${file.name}", DayNotes.serializer(), DayNotes())
                } catch (_: Exception) {
                    null
                }
            }?.toMap() ?: emptyMap()
    }

    suspend fun loadMonthNotes(month: YearMonth): Map<LocalDate, DayNotes> =
        loadAllNotes().filterKeys { YearMonth.from(it) == month }

    /**
     * 保存一条记录：文本 + 媒体（Uri 复制到对应目录）+ 已录制音频文件。
     * 时间戳为保存时刻 yyyy_MM_dd_HH_mm_ss；记录写入目标日期文件。
     */
    suspend fun addNote(
        date: LocalDate,
        text: String,
        imageUris: List<Uri> = emptyList(),
        videoUris: List<Uri> = emptyList(),
        audioUris: List<Uri> = emptyList(),
        recordedAudio: File? = null,
        starred: Boolean = false,
        marker: String = if (starred) MARKER_STAR else MARKER_CIRCLE,
    ) {
        withContext(Dispatchers.IO) {
            val now = LocalDateTime.now()
            val stamp = now.format(stampFormatter)
            val images = imageUris.mapNotNull { copyUriToMedia(it, DIR_MEDIA, stamp) }
            val videos = videoUris.mapNotNull { copyUriToMedia(it, DIR_MEDIA, stamp) }
            val audios = audioUris.mapNotNull { copyUriToMedia(it, DIR_AUDIO, stamp) }.toMutableList()
            recordedAudio?.let { file ->
                moveToMedia(file, DIR_AUDIO, stamp)?.let { audios += it }
            }
            if (text.isBlank() && images.isEmpty() && videos.isEmpty() && audios.isEmpty()) return@withContext
            val entry = NoteEntry(
                time = now.format(timeFormatter),
                text = text.trim(),
                images = images,
                videos = videos,
                audios = audios,
                starred = marker == MARKER_STAR,
                marker = marker,
            )
            val notes = getDayNotes(date)
            saveDayNotes(date, notes.copy(records = (notes.records + entry).sortedBy { it.time }))
            _notesVersion.update { it + 1 }
        }
    }

    /**
     * 通知监听记录：写入今天并打上星标；随后清理所有过期的星标记录。
     * 挂起直到落盘完成，供监听进程（独立进程）等写完后广播通知主进程刷新界面。
     * @return true 表示写入了一条新记录；false 表示当天已有相同内容（去重）跳过。
     */
    suspend fun addNotificationEntry(packageName: String, title: String, body: String): Boolean =
        noteWriteMutex.withLock {
            val appLabel = try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName.substringAfterLast('.')
            }
            val text = buildString {
                append(appLabel)
                if (title.isNotBlank()) {
                    append(" · "); append(title)
                }
                if (body.isNotBlank()) {
                    append("："); append(body)
                }
            }
            // 去重：当天已有相同内容的星标（通知）记录时不再重复写入
            val today = LocalDate.now()
            if (getDayNotes(today).records.any { it.starred && it.text == text }) {
                Log.d(TAG, "重复通知已忽略: $text")
                return@withLock false
            }
            addNote(today, text, marker = MARKER_STAR)
            pruneStarredNotes()
            true
        }

    /**
     * 清理过期临时（星标/通知）记录：早于「保留天数」的星标记录会被移除。
     */
    fun pruneStarredNotes() {
        scope.launch {
            val today = LocalDate.now()
            val cutoff = today.minusDays((_settings.value.starredRetentionDays - 1).toLong())
            loadAllNotes().forEach { (date, notes) ->
                if (date.isBefore(cutoff)) {
                    val kept = notes.records.filterNot { it.effectiveMarker() == MARKER_STAR }
                    if (kept.size != notes.records.size) {
                        saveDayNotes(date, notes.copy(records = kept))
                        _notesVersion.update { it + 1 }
                    }
                }
            }
        }
    }

    /** 调整记录的标记（星=临时 / 圆=正常 / 心=收藏），按 time+text 定位条目。 */
    fun setMarker(date: LocalDate, entry: NoteEntry, marker: String) {
        scope.launch {
            val notes = getDayNotes(date)
            val updated = notes.records.map { e ->
                if (e.time == entry.time && e.text == entry.text) {
                    e.copy(marker = marker, starred = marker == MARKER_STAR)
                } else {
                    e
                }
            }
            if (updated == notes.records) return@launch
            saveDayNotes(date, notes.copy(records = updated))
            _notesVersion.update { it + 1 }
        }
    }

    fun deleteNote(date: LocalDate, entry: NoteEntry) {
        scope.launch {
            val notes = getDayNotes(date)
            val rest = notes.records.filterNot { it.time == entry.time && it.text == entry.text }
            saveDayNotes(date, notes.copy(records = rest))
            (entry.images + entry.videos).forEach { name ->
                File(store.file(DIR_MEDIA), name).delete()
            }
            entry.audios.forEach { name ->
                File(store.file(DIR_AUDIO), name).delete()
            }
            _notesVersion.update { it + 1 }
        }
    }

    /** 更新已有记录的文本内容，按 time+text 定位条目。 */
    fun updateNote(date: LocalDate, entry: NoteEntry, newText: String) {
        scope.launch {
            val notes = getDayNotes(date)
            val updated = notes.records.map { e ->
                if (e.time == entry.time && e.text == entry.text) {
                    e.copy(text = newText.trim())
                } else {
                    e
                }
            }
            if (updated == notes.records) return@launch
            saveDayNotes(date, notes.copy(records = updated))
            _notesVersion.update { it + 1 }
        }
    }

    // ---------- 草稿（随写中断恢复） ----------

    /** 读取草稿（draft.json）；无草稿返回空草稿。 */
    suspend fun loadDraft(): NoteDraft = store.read(FILE_DRAFT, NoteDraft.serializer(), NoteDraft())

    /** 保存草稿：同步落盘，编辑中断（关面板/杀进程）也不丢。 */
    fun saveDraft(draft: NoteDraft) {
        runCatching {
            store.writeSync(FILE_DRAFT, NoteDraft.serializer(), draft)
        }.onFailure { Log.e(TAG, "草稿保存失败", it) }
    }

    /** 清空草稿：删除 draft.json 及草稿目录内的全部媒体文件。 */
    fun clearDraft() {
        store.file(DIR_DRAFTS).listFiles()?.forEach { it.deleteRecursively() }
    }

    /** 把 Uri 内容复制进草稿目录，返回草稿中的文件 Uri（失败返回 null）。 */
    suspend fun importUriToDraft(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        copyUriToMedia(uri, DIR_DRAFTS, LocalDateTime.now().format(stampFormatter))?.let { draftUri(it) }
    }

    /**
     * 异步导入草稿。悬浮窗面板发起拍照/选图时会自动收起、compose 协程已取消，
     * 因此用仓库自身的协程执行，结果通过 onDone 回传。
     */
    fun importToDraftAsync(uri: Uri, onDone: (Uri?) -> Unit) {
        scope.launch {
            onDone(importUriToDraft(uri))
        }
    }

    /** 把已有文件移入草稿目录（拍照/拍视频/录音产物），返回草稿中的文件（失败返回 null）。 */
    fun moveFileToDraft(file: File): File? {
        val name = moveToMedia(file, DIR_DRAFTS, LocalDateTime.now().format(stampFormatter))
        return name?.let { draftFile(it) }
    }

    /** 草稿目录中的文件（即使文件尚未存在）。 */
    fun draftFile(name: String): File = File(store.file(DIR_DRAFTS), name)

    /** 草稿文件的 Uri（供编辑器展示/保存用）。 */
    fun draftUri(name: String): Uri = Uri.fromFile(draftFile(name))

    /**
     * Uri 是草稿目录内已存在的文件时返回文件名，否则返回 null。
     * （录音中/未导入的内容 Uri 不参与草稿持久化）
     */
    fun draftName(uri: Uri): String? {
        if (uri.scheme != "file") return null
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return null
        return name.takeIf { draftFile(name).exists() }
    }

    /** 删除草稿中的单个媒体文件（编辑器里移除媒体时调用）。 */
    fun deleteDraftFile(name: String) {
        draftFile(name).delete()
    }

    /** 导出结果：写入「下载/寄意备份」的文件名 + 打包的文件数量。 */
    data class ExportResult(val fileName: String, val fileCount: Int)

    /**
     * 将全部数据（记录/媒体/邮件/日程/设置）打包成 zip 导出到公共下载目录。
     * 文件数 0 表示当前应用的数据目录里没有数据；返回 null 表示导出失败。
     */
    suspend fun exportAll(): ExportResult? = withContext(Dispatchers.IO) {
        val files = store.file("").listFiles() ?: emptyArray()
        if (files.isEmpty()) return@withContext ExportResult("", 0)
        val stamp = LocalDateTime.now().format(stampFormatter)
        val zipFile = File(context.cacheDir, "note_backup_$stamp.zip")
        try {
            var count = 0
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                fun addRecursively(dir: File, base: String) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isDirectory) {
                            addRecursively(f, "$base/${f.name}")
                        } else {
                            zip.putNextEntry(ZipEntry("$base/${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                            count++
                        }
                    }
                }
                files.forEach { f ->
                    if (f.isDirectory) addRecursively(f, f.name) else {
                        zip.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
                }
            }
            val displayName = "寄意备份_$stamp.zip"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/寄意备份")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                zipFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext null
            zipFile.delete()
            ExportResult(displayName, count)
        } catch (e: Exception) {
            Log.e(TAG, "导出备份失败", e)
            null
        }
    }

    private fun copyUriToMedia(uri: Uri, dir: String, stamp: String): String? {
        return try {
            val ext = extensionOf(uri)
            val target = uniqueTarget(dir, stamp, ext)
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { stream ->
                FileOutputStream(target).use { output -> stream.copyTo(output) }
            }
            target.name
        } catch (e: Exception) {
            Log.e(TAG, "复制媒体失败 $uri", e)
            null
        }
    }

    private fun moveToMedia(file: File, dir: String, stamp: String): String? = try {
        val ext = file.extension.ifBlank { "wav" }
        val target = uniqueTarget(dir, stamp, ".$ext")
        target.parentFile?.mkdirs()
        if (file.renameTo(target)) target.name else {
            file.copyTo(target, overwrite = true)
            file.delete()
            target.name
        }
    } catch (e: Exception) {
        Log.e(TAG, "移动媒体失败 $file", e)
        null
    }

    /** 同一秒内多次添加媒体时追加序号，避免文件名冲突互相覆盖。 */
    private fun uniqueTarget(dir: String, stamp: String, ext: String): File {
        var target = File(store.file(dir), "$stamp$ext")
        var i = 1
        while (target.exists()) {
            target = File(store.file(dir), "${stamp}_$i$ext")
            i++
        }
        return target
    }

    private fun extensionOf(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        val extFromMime = when (mime?.lowercase()) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "image/heic" -> ".heic"
            "video/mp4" -> ".mp4"
            "video/3gpp" -> ".3gp"
            "video/webm" -> ".webm"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/mpeg" -> ".mp3"
            "audio/mp4" -> ".m4a"
            "audio/ogg" -> ".ogg"
            "audio/aac" -> ".aac"
            "audio/amr" -> ".amr"
            else -> null
        }
        if (extFromMime != null) return extFromMime
        val displayName = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val dot = displayName?.lastIndexOf('.')
        return if (dot != null && dot > 0 && dot < displayName!!.length - 1) {
            "." + displayName.substring(dot + 1)
        } else ".bin"
    }

    // ---------- 日程 / 邮件 / 设置 ----------

    fun addSchedule(item: ScheduleItem) {
        _schedules.update { it + item }
        persistSchedules()
    }

    fun toggleSchedule(id: String) {
        _schedules.update { list ->
            list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        }
        persistSchedules()
    }

    fun removeSchedule(id: String) {
        _schedules.update { list -> list.filterNot { it.id == id } }
        persistSchedules()
    }

    fun addEmail(title: String, content: String) {
        val email = EmailRecord(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            content = content.trim(),
            createdAt = System.currentTimeMillis(),
        )
        if (email.title.isEmpty() && email.content.isEmpty()) return
        _emails.update { (listOf(email) + it).sortedByDescending { e -> e.createdAt } }
        persistEmails()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        persistSettings()
    }

    fun updateSetting(key: String, value: Boolean) {
        updateSettings { settings ->
            when (key) {
                "dndEnabled" -> settings.copy(dndEnabled = value)
                "notificationListening" -> settings.copy(notificationListening = value)
                "reminderEmails" -> settings.copy(reminderEmails = value)
                  "quickRecordEnabled" -> settings.copy(quickRecordEnabled = value)
                  "listenerConnected" -> settings.copy(listenerConnected = value)
                  else -> settings
            }
        }
    }

    fun toggleMonitoredPackage(packageName: String) {
        updateSettings { settings ->
            val current = settings.monitoredPackages
            settings.copy(
                monitoredPackages = if (packageName in current) {
                    current - packageName
                } else {
                    current + packageName
                },
            )
        }
    }

    fun addDndRule(name: String, startMinute: Int, endMinute: Int, days: List<Int>) {
        updateSettings { settings ->
            val rule = DndRule(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { "勿扰规则" },
                days = days,
                startMinute = startMinute,
                endMinute = endMinute,
            )
            settings.copy(dndRules = settings.dndRules + rule)
        }
    }

    fun updateDndRule(id: String, name: String, startMinute: Int, endMinute: Int, days: List<Int>) {
        updateSettings { settings ->
            settings.copy(
                dndRules = settings.dndRules.map {
                    if (it.id == id) {
                        it.copy(
                            name = name.trim().ifEmpty { "勿扰规则" },
                            days = days,
                            startMinute = startMinute,
                            endMinute = endMinute,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun removeDndRule(id: String) {
        updateSettings { settings ->
            settings.copy(dndRules = settings.dndRules.filterNot { it.id == id })
        }
    }

    fun updateThemePreset(id: String) {
        updateSettings { it.copy(themePreset = id) }
    }

    fun updateThemeMode(mode: String) {
        updateSettings { it.copy(themeMode = mode) }
    }

    /**
     * 通知监听进程用：进程刚启动时设置可能还没从磁盘读完，
     * 若内存值仍是默认值则同步读盘一次，避免把监听开关误判为关闭而漏记。
     */
    fun currentSettings(): AppSettings = _settings.value

    /** 主进程收到监听进程广播后调用：让界面重新从磁盘加载最新数据。 */
    fun bumpNotesVersion() {
        _notesVersion.update { it + 1 }
    }

    /** 主进程收到监听进程广播后调用：重新从磁盘读设置（listenerConnected 等由监听进程更新）。 */
    fun reloadSettings() {
        _settings.value = store.readSync(FILE_SETTINGS, AppSettings.serializer(), _settings.value)
    }

    private fun persistSchedules() =
        scope.launch { store.write(FILE_SCHEDULES, ListSerializer(ScheduleItem.serializer()), _schedules.value) }

    private fun persistEmails() =
        scope.launch { store.write(FILE_EMAILS, ListSerializer(EmailRecord.serializer()), _emails.value) }

    private fun persistSettings() {
        val value = _settings.value
        runCatching {
            store.writeSync(FILE_SETTINGS, AppSettings.serializer(), value)
            Log.d(
                TAG,
                "设置已保存: 监听=${value.notificationListening} 随时记=${value.quickRecordEnabled} " +
                    "主题=${value.themePreset}/${value.themeMode} 勿扰=${value.dndEnabled} " +
                    "提醒=${value.reminderEmails} 临时记录保留=${value.starredRetentionDays}天 " +
                    "监控应用=${value.monitoredPackages}",
            )
        }.onFailure { Log.e(TAG, "设置落盘失败", it) }
    }

    companion object {
        const val DIR_NOTES = "notes"
        const val DIR_TEXT = "notes/text"
        const val DIR_MEDIA = "notes/image_and_video"
        const val DIR_AUDIO = "notes/audio"
        const val DIR_DRAFTS = "notes/drafts"
        const val FILE_DRAFT = "notes/drafts/draft.json"

        @Volatile
        private var instance: NoteRepository? = null

        fun get(context: Context): NoteRepository =
            instance ?: synchronized(this) {
                instance ?: NoteRepository(context.applicationContext).also { instance = it }
            }

        private fun textFileName(date: LocalDate): String =
            "$DIR_TEXT/${date.format(dateFileFormatter)}_text.json"

        private fun parseDateFromFileName(name: String): LocalDate? = try {
            LocalDate.parse(name.removeSuffix("_text.json"), dateFileFormatter)
        } catch (_: Exception) {
            null
        }

        private val dateFileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
        private val stampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
        private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        const val FILE_SCHEDULES = "schedules/schedules.json"
        const val FILE_EMAILS = "emails/emails.json"
        const val FILE_SETTINGS = "settings.json"

        /** 监听进程 → 主进程的数据变更广播（跨进程不共享 StateFlow，靠广播刷新界面）。 */
        const val ACTION_DATA_CHANGED = "com.bicy.note.ACTION_DATA_CHANGED"

        const val TAG = "NoteRepository"
    }
}