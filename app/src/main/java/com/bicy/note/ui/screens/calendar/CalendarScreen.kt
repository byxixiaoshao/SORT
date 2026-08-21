package com.bicy.note.ui.screens.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.model.DayNotes
import com.bicy.note.ui.components.MonthCalendar
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

enum class ChartMode(val label: String) {
    Day24("近1天"),
    Week7("近7天"),
    Month30("近30天"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onDateClick: (LocalDate) -> Unit,
) {
    val repository = LocalRepository.current
    val notesVersion by repository.notesVersion.collectAsStateWithLifecycle()

    var allNotes by remember { mutableStateOf<Map<LocalDate, DayNotes>>(emptyMap()) }
    LaunchedEffect(notesVersion) {
        allNotes = repository.loadAllNotes()
    }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var chartMode by remember { mutableStateOf(ChartMode.Day24) }

    val timestamps = remember(allNotes) {
        allNotes.entries.flatMap { (date, notes) ->
            notes.records.mapNotNull { entry ->
                try {
                    date.atTime(LocalTime.parse(entry.time))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    val markedDates = remember(allNotes) { allNotes.keys }
    val frequency = remember(timestamps, chartMode) {
        computeFrequency(timestamps, chartMode)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            MonthCalendar(
                month = month,
                markedDates = markedDates,
                selectedDate = null,
                onMonthChange = { month = it },
                onDateClick = onDateClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChartMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = chartMode == mode,
                        onClick = { chartMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, ChartMode.entries.size),
                    ) {
                        Text(text = mode.label)
                    }
                }
            }
FrequencyChart(
                data = frequency.values,
                labels = frequency.labels,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp),
            )
        }
    }
}