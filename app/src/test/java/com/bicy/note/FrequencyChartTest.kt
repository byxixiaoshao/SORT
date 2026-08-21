package com.bicy.note

import com.bicy.note.ui.screens.calendar.ChartMode
import com.bicy.note.ui.screens.calendar.computeFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FrequencyChartTest {

    private fun todayAt(hour: Int): Long {
        return LocalDate.now().atTime(hour, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun `Day24 mode returns 24 values`() {
        val timestamps = listOf(todayAt(8), todayAt(14), todayAt(8))
        val result = computeFrequency(timestamps, ChartMode.Day24)
        assertEquals(24, result.values.size)
        assertEquals(2, result.values[8])
        assertEquals(1, result.values[14])
        assertEquals(0, result.values[0])
    }

    @Test
    fun `Week7 mode returns 7 values`() {
        val timestamps = listOf(todayAt(10))
        val result = computeFrequency(timestamps, ChartMode.Week7)
        assertEquals(7, result.values.size)
        assertEquals(7, result.labels.size)
    }

    @Test
    fun `Month30 mode returns 30 values`() {
        val timestamps = listOf(todayAt(10))
        val result = computeFrequency(timestamps, ChartMode.Month30)
        assertEquals(30, result.values.size)
        assertEquals(30, result.labels.size)
    }

    @Test
    fun `Empty timestamps produces all zeros`() {
        val result = computeFrequency(emptyList(), ChartMode.Day24)
        assertEquals(List(24) { 0 }, result.values)
    }

    @Test
    fun `Day24 labels every 6th hour has label`() {
        val result = computeFrequency(emptyList(), ChartMode.Day24)
        assertEquals("0", result.labels[0])
        assertEquals("6", result.labels[6])
        assertEquals("12", result.labels[12])
        assertEquals("18", result.labels[18])
        assertNull(result.labels[1])
        assertNull(result.labels[7])
    }
}
