package com.bicy.note.ui.screens.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bicy.note.util.toLocalDate
import com.bicy.note.util.toLocalDateTime
import java.time.LocalDate

data class FrequencyData(
    val values: List<Int>,
    val labels: List<String?>,
)

fun computeFrequency(timestamps: List<Long>, mode: ChartMode): FrequencyData {
    val today = LocalDate.now()
    return when (mode) {
        ChartMode.Day24 -> {
            val counts = IntArray(24)
            timestamps.forEach { ts ->
                val dt = ts.toLocalDateTime()
                if (dt.toLocalDate() == today) counts[dt.hour]++
            }
            FrequencyData(
                values = counts.toList(),
                labels = List(24) { hour -> if (hour % 6 == 0) "$hour" else null },
            )
        }
        ChartMode.Week7 -> {
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val counts = IntArray(7)
            timestamps.forEach { ts ->
                val index = days.indexOf(ts.toLocalDate())
                if (index >= 0) counts[index]++
            }
            FrequencyData(
                values = counts.toList(),
                labels = days.map { it.dayOfMonth.toString() },
            )
        }
        ChartMode.Month30 -> {
            val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
            val counts = IntArray(30)
            timestamps.forEach { ts ->
                val index = days.indexOf(ts.toLocalDate())
                if (index >= 0) counts[index]++
            }
            FrequencyData(
                values = counts.toList(),
                labels = days.mapIndexed { index, day ->
                    if (index % 5 == 0 || index == 29) "${day.monthValue}/${day.dayOfMonth}" else null
                },
            )
        }
    }
}

@Composable
fun FrequencyChart(
    data: List<Int>,
    labels: List<String?>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    if (data.all { it == 0 }) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无记录",
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
            )
        }
        return
    }

    val maxValue = (data.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier = modifier) {
        val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
        val labelHeight = 14.dp.toPx()
        val chartBottom = size.height - labelHeight - 4.dp.toPx()
        val chartTop = 16.dp.toPx()

        val leftPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val chartWidth = size.width - leftPad - rightPad
        val chartHeight = chartBottom - chartTop

        for (i in 0..4) {
            val y = chartTop + chartHeight * i / 4f
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + chartWidth, y),
                strokeWidth = 1f,
            )
        }

        val maxLabel = textMeasurer.measure(text = "$maxValue", style = labelStyle)
        drawText(
            textLayoutResult = maxLabel,
            topLeft = Offset(leftPad + chartWidth - maxLabel.size.width - 4.dp.toPx(), 0f),
        )

        if (data.size > 1) {
            val step = chartWidth / (data.size - 1)
            val path = Path()
            val points = data.mapIndexed { index, value ->
                Offset(
                    x = leftPad + index * step,
                    y = chartBottom - if (maxValue == 0) 0f else chartHeight * value / maxValue,
                )
            }
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
            points.forEach { point ->
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = point)
            }
        }

        val labelStep = chartWidth / (data.size - 1)
        labels.forEachIndexed { index, label ->
            if (label != null && data.size > 1) {
                val text = textMeasurer.measure(text = label, style = labelStyle)
                val x = leftPad + index * labelStep - text.size.width / 2f
                val clampedX = x.coerceIn(0f, size.width - text.size.width)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(clampedX, chartBottom + 6.dp.toPx()),
                )
            }
        }
    }
}