package com.thetuner.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class YinPitchDetectorTest {

    private val sampleRate = 44100
    private val bufferSize = 4096
    private val detector = YinPitchDetector(sampleRate = sampleRate, bufferSize = bufferSize)

    private fun generateSineWave(frequency: Double, samples: Int = bufferSize): FloatArray {
        return FloatArray(samples) {
            sin(2.0 * PI * frequency * it / sampleRate).toFloat()
        }
    }

    @Test
    fun `detects A4 at 440 Hz within 1 Hz`() {
        val signal = generateSineWave(440.0)
        val result = detector.detect(signal)
        assertNotNull("Should detect pitch for 440 Hz sine wave", result)
        assertEquals(440f, result!!.frequencyHz, 1f)
    }

    @Test
    fun `detects E2 at 82_4 Hz without octave error`() {
        val signal = generateSineWave(82.4)
        val result = detector.detect(signal)
        assertNotNull("Should detect pitch for 82.4 Hz sine wave", result)
        assertEquals(82.4f, result!!.frequencyHz, 1f)
    }

    @Test
    fun `detects E4 at 329_6 Hz within 1 Hz`() {
        val signal = generateSineWave(329.6)
        val result = detector.detect(signal)
        assertNotNull("Should detect pitch for 329.6 Hz sine wave", result)
        assertEquals(329.6f, result!!.frequencyHz, 1f)
    }

    @Test
    fun `returns null for silent buffer`() {
        val silence = FloatArray(bufferSize) { 0f }
        val result = detector.detect(silence)
        assertNull("Should return null for silence", result)
    }

    @Test
    fun `returns null or low confidence for low amplitude noise`() {
        val noise = FloatArray(bufferSize) { (Math.random() * 0.001).toFloat() }
        val result = detector.detect(noise)
        // Either null (no pitch detected) or very low confidence
        if (result != null) {
            assertTrue(
                "Noise should have low confidence, got ${result.confidence}",
                result.confidence < 0.5f
            )
        }
    }
}
