package com.bicy.note.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

/**
 * 共享月历：月头（翻月）+ 星期行 + 日期网格（标记点/今日/选中）。
 * 日历页与悬浮窗面板复用。
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = "上个月",
                )
            }
            Text(
                text = "${month.year}年 ${month.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "下个月",
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MonthGrid(
            month = month,
            markedDates = markedDates,
            selectedDate = selectedDate,
            onDateClick = onDateClick,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val totalWeeks = (leading + daysInMonth + 6) / 7

    Column(modifier = Modifier.fillMaxWidth()) {
        var week = 0
        while (week < totalWeeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = week * 7 + col - leading
                    val date = if (index in 0 until daysInMonth) month.atDay(index + 1) else null
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date != null && date == selectedDate,
                        hasRecords = date != null && date in markedDates,
                        onClick = { if (date != null) onDateClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            week++
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    hasRecords: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .then(if (date == null) Modifier else Modifier.clickable { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .background(
                            color = if (hasRecords) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}