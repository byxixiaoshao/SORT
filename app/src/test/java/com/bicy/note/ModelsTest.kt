package com.bicy.note

import com.bicy.note.data.model.AlarmRule
import com.bicy.note.data.model.DayNotes
import com.bicy.note.data.model.DndRule
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.MARKER_HEART
import com.bicy.note.data.model.MARKER_STAR
import com.bicy.note.data.model.NoteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ModelsTest {

    @Test
    fun `effectiveMarker returns marker when set`() {
        val entry = NoteEntry(time = "10:00:00", text = "test", marker = MARKER_HEART)
        assertEquals(MARKER_HEART, entry.effectiveMarker())
    }

    @Test
    fun `effectiveMarker falls back to starred for old data`() {
        val entry = NoteEntry(time = "10:00:00", text = "test", starred = true)
        assertEquals(MARKER_STAR, entry.effectiveMarker())
    }

    @Test
    fun `effectiveMarker defaults to circle`() {
        val entry = NoteEntry(time = "10:00:00", text = "test")
        assertEquals(MARKER_CIRCLE, entry.effectiveMarker())
    }

    @Test
    fun `DayNotes isEmpty when no records`() {
        val notes = DayNotes()
        assertTrue(notes.records.isEmpty())
    }

    @Test
    fun `AlarmRule default values`() {
        val rule = AlarmRule(id = "1")
        assertEquals("闹钟", rule.name)
        assertEquals((1..7).toList(), rule.days)
        assertEquals(7 * 60, rule.minuteOfDay)
        assertTrue(rule.enabled)
    }

    @Test
    fun `DndRule default values`() {
        val rule = DndRule(id = "1")
        assertEquals("勿扰规则", rule.name)
        assertEquals((1..7).toList(), rule.days)
        assertEquals(22 * 60, rule.startMinute)
        assertEquals(7 * 60, rule.endMinute)
        assertTrue(rule.enabled)
    }
}
