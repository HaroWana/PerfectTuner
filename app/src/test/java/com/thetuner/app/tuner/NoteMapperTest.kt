package com.thetuner.app.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMapperTest {

    @Test
    fun `440 Hz maps to A4 midi 69`() {
        val note = NoteMapper.frequencyToNote(440.0f)
        assertEquals("A", note.name)
        assertEquals(4, note.octave)
        assertEquals(69, note.midiNumber)
    }

    @Test
    fun `82_41 Hz maps to E2`() {
        val note = NoteMapper.frequencyToNote(82.41f)
        assertEquals("E", note.name)
        assertEquals(2, note.octave)
    }

    @Test
    fun `329_63 Hz maps to E4`() {
        val note = NoteMapper.frequencyToNote(329.63f)
        assertEquals("E", note.name)
        assertEquals(4, note.octave)
    }

    @Test
    fun `261_63 Hz maps to C4`() {
        val note = NoteMapper.frequencyToNote(261.63f)
        assertEquals("C", note.name)
        assertEquals(4, note.octave)
    }

    @Test
    fun `cents between 441 and 440 is positive (sharp)`() {
        val cents = NoteMapper.centsBetween(441.0f, 440.0f)
        assertEquals(3.93f, cents, 0.1f)
    }

    @Test
    fun `cents between 439 and 440 is negative (flat)`() {
        val cents = NoteMapper.centsBetween(439.0f, 440.0f)
        assertEquals(-3.94f, cents, 0.1f)
    }

    @Test
    fun `cents between 440 and 440 is zero`() {
        val cents = NoteMapper.centsBetween(440.0f, 440.0f)
        assertEquals(0.0f, cents, 0.001f)
    }
}
