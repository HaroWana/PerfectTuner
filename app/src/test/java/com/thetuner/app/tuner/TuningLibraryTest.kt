package com.thetuner.app.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningLibraryTest {

    private val sharps = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    @Test
    fun `every tuning has six strings with consecutive indices`() {
        val tunings = TuningLibrary.allTunings.values.filter { it.strings.isNotEmpty() }
        assertTrue(tunings.size > 20)
        for (tuning in tunings) {
            assertEquals("${tuning.id} string count", 6, tuning.strings.size)
            tuning.strings.forEachIndexed { index, string ->
                assertEquals("${tuning.id}[$index] index", index, string.index)
            }
        }
    }

    @Test
    fun `string note names and octaves match their midi numbers`() {
        for (tuning in TuningLibrary.allTunings.values) {
            for (string in tuning.strings) {
                val pitchClass = string.midiNumber % 12
                assertTrue(
                    "${tuning.id}: ${string.noteName} does not match midi ${string.midiNumber}",
                    string.noteName == sharps[pitchClass] || string.noteName == flats[pitchClass]
                )
                assertEquals(
                    "${tuning.id}: octave of ${string.noteName}${string.octave}",
                    (string.midiNumber / 12) - 1,
                    string.octave
                )
            }
        }
    }

    @Test
    fun `chromatic mode is never locked`() {
        assertTrue(TuningLibrary.isFree(TuningLibrary.CHROMATIC_MODE.id))
    }

    @Test
    fun `findById falls back to standard for unknown ids`() {
        assertEquals(STANDARD_TUNING, TuningLibrary.findById("no_such_tuning"))
    }
}
