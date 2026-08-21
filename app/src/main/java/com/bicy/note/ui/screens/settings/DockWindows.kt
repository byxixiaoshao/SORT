package com.bicy.note.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.ScheduleItem
import com.bicy.note.ui.components.MarkerToggleSegments
import com.bicy.note.ui.components.markerIcon
import com.bicy.note.ui.components.markerOptions
import com.bicy.note.ui.search.SearchHit
import com.bicy.note.ui.search.searchAll
import com.bicy.note.util.formatDateTime
import com.bicy.note.util.formatMinuteOfDay
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun QuickNoteWindow(
    clearSignal: Int = 0,
    onClearConsumed: () -> Unit = {},
) {
    var marker by remember { mutableStateOf(MARKER_CIRCLE) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "随写",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
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
                            .clickable { marker = value },
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
        QuickNoteComposer(
            date = LocalDate.now(),
            clearSignal = clearSignal,
            onClearConsumed = onClearConsumed,
            marker = marker,
            onMarkerChange = { marker = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleWindow() {
    val repository = LocalRepository.current
    val schedules by repository.schedules.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var typeIndex by remember { mutableStateOf(0) }
    var day by remember { mutableStateOf(1) }
    var startText by remember { mutableStateOf("08:00") }
    var endText by remember { mutableStateOf("08:45") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "日程表",
            style = MaterialTheme.typography.titleMedium,
        )

        if (schedules.isEmpty() && !showForm) {
            Text(
                text = "还没有日程，点击下方按钮添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showForm) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "日程名称（如：数学课 / 午休）") },
                singleLine = true,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("课程", "作息").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = typeIndex == index,
                        onClick = { typeIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(text = label)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                    FilterChip(
                        selected = day == index + 1,
                        onClick = { day = index + 1 },
                        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "开始") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "结束") },
                    singleLine = true,
                )
            }
            Button(
                onClick = {
                    val start = parseMinute(startText)
                    val end = parseMinute(endText)
                    if (title.isNotBlank() && start != null && end != null) {
                        repository.addSchedule(
                            ScheduleItem(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                type = if (typeIndex == 0) "course" else "routine",
                                dayOfWeek = day,
                                startMinute = start,
                                endMinute = end,
                            )
                        )
                        title = ""
                        showForm = false
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text(text = "添加日程")
            }
        }

        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(schedules, key = { it.id }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    onToggle = { repository.toggleSchedule(schedule.id) },
                    onDelete = { repository.removeSchedule(schedule.id) },
                )
            }
        }

        if (!showForm) {
            Button(
                onClick = { showForm = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "＋ 添加日程")
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: ScheduleItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "周${"一二三四五六日"[schedule.dayOfWeek - 1]} " +
                    "${formatMinuteOfDay(schedule.startMinute)} - ${formatMinuteOfDay(schedule.endMinute)} " +
                    "· ${if (schedule.type == "course") "课程" else "作息"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = schedule.enabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun EmailWindow() {
    val repository = LocalRepository.current
    val emails by repository.emails.collectAsStateWithLifecycle()

    var showCompose by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "邮箱",
            style = MaterialTheme.typography.titleMedium,
        )
        if (emails.isEmpty() && !showCompose) {
            Text(
                text = "还没有回忆邮件，写一封寄给未来的自己吧",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCompose) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "标题（如：写给 5 年后的自己）") },
                singleLine = true,
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "内容…") },
                minLines = 3,
            )
            Button(
                onClick = {
                    repository.addEmail(title, content)
                    title = ""
                    content = ""
                    showCompose = false
                },
                enabled = title.isNotBlank() || content.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "存入邮箱")
            }
        }
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
            items(emails, key = { it.id }) { email ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = email.title.ifEmpty { "（无标题）" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = email.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        text = email.createdAt.formatDateTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (!showCompose) {
            Button(
                onClick = { showCompose = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "＋ 写邮件")
            }
        }
    }
}

@Composable
fun SearchWindow() {
    val repository = LocalRepository.current
    val schedules by repository.schedules.collectAsStateWithLifecycle()
    val emails by repository.emails.collectAsStateWithLifecycle()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var query by remember { mutableStateOf("") }
    var activeMarkers by remember { mutableStateOf(emptySet<String>()) }
    var results by remember { mutableStateOf(emptyList<SearchHit>()) }
    LaunchedEffect(query, month, activeMarkers) {
        results = if (query.isBlank()) {
            emptyList()
        } else {
            searchAll(repository, query).filter { hit ->
                val inMonth = hit.date == null || YearMonth.from(hit.date) == month
                val markerOk = if (hit.entry != null) {
                    activeMarkers.isEmpty() || hit.entry.effectiveMarker() in activeMarkers
                } else {
                    true
                }
                inMonth && markerOk
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkerToggleSegments(
                activeMarkers = activeMarkers,
                onToggle = { value ->
                    activeMarkers = if (value in activeMarkers) {
                        activeMarkers - value
                    } else {
                        activeMarkers + value
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "上个月",
                )
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "下个月",
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "搜索记录、日程、邮件…") },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            singleLine = true,
        )
        if (query.isBlank()) {
            Text(
                text = "搜索限定在 ${month.year}年${month.monthValue}月内；日程每周固定，始终显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (results.isEmpty()) {
            Text(
                text = "未找到相关内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(results, key = { "${it.kind}-${it.title}-${it.timestamp}" }) { hit ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = hit.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                        )
                        Text(
                            text = "${hit.kind} · ${hit.subtitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun parseMinute(text: String): Int? {
    return try {
        val match = Regex("""(\d{1,2}):(\d{2})""").find(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    } catch (_: Exception) {
        null
    }
}