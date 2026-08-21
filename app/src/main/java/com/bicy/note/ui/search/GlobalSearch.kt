package com.bicy.note.ui.search

import com.bicy.note.data.NoteRepository
import com.bicy.note.data.model.EmailRecord
import com.bicy.note.data.model.NoteEntry
import com.bicy.note.data.model.ScheduleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SearchHit(
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val kind: String,
    /** 记录结果：所属日期与对应条目，用于点击跳转 */
    val date: LocalDate? = null,
    val entry: NoteEntry? = null,
)

suspend fun searchAll(
    repository: NoteRepository,
    query: String,
): List<SearchHit> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.isEmpty()) return@withContext emptyList()

    val hits = mutableListOf<SearchHit>()

    repository.loadAllNotes().forEach { (date, notes) ->
        notes.records.filter { it.text.contains(q, ignoreCase = true) }.forEach { entry ->
            hits += SearchHit(
                title = entry.text,
                subtitle = "${date.format(dateFormatter)} ${entry.time}",
                timestamp = date.atTime(parseTime(entry.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                kind = "记录",
                date = date,
                entry = entry,
            )
        }
    }

    repository.schedules.value
        .filter { it.title.contains(q, ignoreCase = true) }
        .forEach { schedule ->
            hits += SearchHit(
                title = schedule.title,
                subtitle = "${schedule.type} · 周${schedule.dayOfWeek}",
                timestamp = 0L,
                kind = "日程",
            )
        }

    repository.emails.value
        .filter { it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true) }
        .forEach { email ->
            hits += SearchHit(
                title = email.title.ifEmpty { "（无标题）" },
                subtitle = email.content.take(30),
                timestamp = email.createdAt,
                kind = "邮件",
                date = Instant.ofEpochMilli(email.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
            )
        }

    hits.sortedByDescending { it.timestamp }
}

private fun parseTime(text: String): LocalTime = try {
    LocalTime.parse(text)
} catch (_: Exception) {
    LocalTime.MIDNIGHT
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun Long.formatDateTime(): String =
    java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))