package com.bicy.note.data.model

import kotlinx.serialization.Serializable

/** 记录标记：星=临时（通知自动记录，过期自动移除），圆=正常记录，心=收藏。 */
const val MARKER_STAR = "star"
const val MARKER_CIRCLE = "circle"
const val MARKER_HEART = "heart"

/**
 * 一条记录（对应某一天 text 文件中的一条）。
 * time 为保存时刻 HH:mm:ss；images/videos/audios 为文件名（不含路径）。
 * marker 为标记；旧数据无 marker 时由 starred 推导（starred=true 视为星标）。
 */
@Serializable
data class NoteEntry(
    val time: String,
    val text: String = "",
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val audios: List<String> = emptyList(),
    val starred: Boolean = false,
    val marker: String? = null,
) {
    /** 生效标记：新数据看 marker，旧数据按 starred 推导。 */
    fun effectiveMarker(): String =
        marker ?: if (starred) MARKER_STAR else MARKER_CIRCLE
}

/** 某一天的全部记录，对应 notes/text/yyyy_MM_dd_text.json */
@Serializable
data class DayNotes(
    val version: Int = 1,
    val records: List<NoteEntry> = emptyList(),
)

/**
 * 随写草稿：对应 notes/drafts/draft.json，媒体文件名存放在草稿目录内。
 * 编辑中断（关面板/杀进程）时自动保存，下次编辑自动恢复。
 */
@Serializable
data class NoteDraft(
    val text: String = "",
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val selectedAudio: String? = null,
    val recordedAudio: String? = null,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && images.isEmpty() && videos.isEmpty() &&
            selectedAudio == null && recordedAudio == null
}

@Serializable
data class ScheduleItem(
    val id: String,
    val title: String,
    val type: String = "course",
    val dayOfWeek: Int = 1,
    val startMinute: Int = 480,
    val endMinute: Int = 510,
    val enabled: Boolean = true,
)

@Serializable
data class EmailRecord(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val sentAt: Long? = null,
)

/** 定时勿扰规则（应用内自管，由 ScheduledDnd 闹钟调度实施） */
@Serializable
data class DndRule(
    val id: String,
    val name: String = "勿扰规则",
    /** 生效星期：1=周一 .. 7=周日 */
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val enabled: Boolean = true,
)

/** 闹钟规则（应用内自管，由 ScheduledAlarm 闹钟调度实施） */
@Serializable
data class AlarmRule(
    val id: String,
    val name: String = "闹钟",
    /** 生效星期：1=周一 .. 7=周日 */
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    /** 闹钟时间（当日分钟数，0~1439） */
    val minuteOfDay: Int = 7 * 60,
    val enabled: Boolean = true,
)

@Serializable
data class AppSettings(
    val dndEnabled: Boolean = false,
    val dndStartMinute: Int = 22 * 60,
    val dndEndMinute: Int = 7 * 60,
    val dndRules: List<DndRule> = emptyList(),
    val alarmRules: List<AlarmRule> = emptyList(),
    val alarmRingtoneUri: String? = null,
    val notificationListening: Boolean = false,
    val reminderEmails: Boolean = true,
    val quickRecordEnabled: Boolean = false,
      val monitoredPackages: List<String> = emptyList(),
      val listenerConnected: Boolean = false,
      val themePreset: String = "indigo",
    val themeMode: String = "light",
    /** 临时记录（通知自动记录）保留天数：1~30，到期自动清理 */
    val starredRetentionDays: Int = 1,
)
