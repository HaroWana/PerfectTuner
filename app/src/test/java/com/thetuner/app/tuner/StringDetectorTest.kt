package com.thetuner.app.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.math.pow

class StringDetectorTest {

    private lateinit var detector: StringDetector
    private lateinit var frequencies: List<Float>

    @Before
    fun setup() {
        detector = StringDetector()
        frequencies = STANDARD_TUNING.frequencies()
    }

    // --- GuitarTuning frequency tests ---

    @Test
    fun `STANDARD_TUNING has 6 strings`() {
        assertEquals(6, STANDARD_TUNING.strings.size)
    }

    @Test
    fun `STANDARD_TUNING has correct MIDI numbers`() {
        val expectedMidi = listOf(40, 45, 50, 55, 59, 64)
        assertEquals(expectedMidi, STANDARD_TUNING.strings.map { it.midiNumber })
    }

    @Test
    fun `frequencies computes correct Hz for A4=440`() {
        val freqs = STANDARD_TUNING.frequencies()
        // E2=82.41, A2=110.0, D3=146.83, G3=196.0, B3=246.94, E4=329.63
        assertEquals(82.41f, freqs[0], 0.5f)
        assertEquals(110.0f, freqs[1], 0.5f)
        assertEquals(146.83f, freqs[2], 0.5f)
        assertEquals(196.0f, freqs[3], 0.5f)
        assertEquals(246.94f, freqs[4], 0.5f)
        assertEquals(329.63f, freqs[5], 0.5f)
    }

    // --- StringDetector.detect() basic tests ---

    @Test
    fun `detect returns null on first frame (hysteresis not met)`() {
        // First detection of string 0 - hysteresis count is 1, needs 3
        assertNull(detector.detect(frequencies[0], frequencies))
    }

    @Test
    fun `detect returns string index after 3 consecutive frames`() {
        // E2 - string index 0
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)
        val result = detector.detect(frequencies[0], frequencies)
        assertEquals(0, result)
    }

    @Test
    fun `detect returns A2 string after 3 frames`() {
        detector.detect(frequencies[1], frequencies)
        detector.detect(frequencies[1], frequencies)
        val result = detector.detect(frequencies[1], frequencies)
        assertEquals(1, result)
    }

    @Test
    fun `detect returns E4 string after 3 frames`() {
        detector.detect(frequencies[5], frequencies)
        detector.detect(frequencies[5], frequencies)
        val result = detector.detect(frequencies[5], frequencies)
        assertEquals(5, result)
    }

    // --- Hysteresis behavior ---

    @Test
    fun `stays on current string without hysteresis`() {
        // Lock on to string 0
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)

        // Continue detecting same string -- should return immediately
        val result = detector.detect(frequencies[0], frequencies)
        assertEquals(0, result)
    }

    @Test
    fun `switching strings requires 3 frames of new string`() {
        // Lock on to string 0 (E2)
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies) // locked on 0

        // Now detect string 1 (A2) - should still return 0 during transition
        assertEquals(0, detector.detect(frequencies[1], frequencies)) // candidate=1, count=1
        assertEquals(0, detector.detect(frequencies[1], frequencies)) // candidate=1, count=2
        assertEquals(1, detector.detect(frequencies[1], frequencies)) // candidate=1, count=3 -> switch
    }

    @Test
    fun `interrupted switch resets hysteresis counter`() {
        // Lock on to string 0
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)

        // Start switching to string 1
        detector.detect(frequencies[1], frequencies) // count=1
        detector.detect(frequencies[1], frequencies) // count=2

        // Interrupted by going back to string 0
        val result = detector.detect(frequencies[0], frequencies) // back to current, no switch
        assertEquals(0, result)
    }

    // --- Out of range ---

    @Test
    fun `detect returns null when pitch is more than 200 cents from all strings`() {
        // 50 Hz is far from E2 (82.41 Hz) - more than 200 cents away
        // cents = 1200 * log2(50 / 82.41) = ~-867 cents
        detector.detect(50f, frequencies)
        detector.detect(50f, frequencies)
        val result = detector.detect(50f, frequencies)
        assertNull(result)
    }

    // --- Reset ---

    @Test
    fun `reset clears all state`() {
        // Lock on to string 0
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)
        detector.detect(frequencies[0], frequencies)

        detector.reset()

        // After reset, should need 3 frames again
        assertNull(detector.detect(frequencies[0], frequencies))
    }

    // --- Slightly off-pitch detection ---

    @Test
    fun `detects correct string when slightly sharp`() {
        // A2 = 110 Hz, slightly sharp at 111 Hz (~15.7 cents sharp)
        detector.detect(111f, frequencies)
        detector.detect(111f, frequencies)
        val result = detector.detect(111f, frequencies)
        assertEquals(1, result)
    }

    @Test
    fun `detects correct string when slightly flat`() {
        // A2 = 110 Hz, slightly flat at 109 Hz (~15.9 cents flat)
        detector.detect(109f, frequencies)
        detector.detect(109f, frequencies)
        val result = detector.detect(109f, frequencies)
        assertEquals(1, result)
    }
}
