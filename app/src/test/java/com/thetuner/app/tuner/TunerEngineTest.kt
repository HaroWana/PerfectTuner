package com.thetuner.app.tuner

import com.thetuner.app.audio.AudioSource
import com.thetuner.app.detection.PitchDetector
import com.thetuner.app.detection.PitchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class TunerEngineTest {

    private class FakeAudioSource(private val frameCount: Int) : AudioSource {
        // 0.1 amplitude -> -20 dBFS, well above the -50 dBFS silence gate
        override fun frames(): Flow<FloatArray> = flow {
            repeat(frameCount) { emit(FloatArray(1024) { 0.1f }) }
        }

        override fun start() {}
        override fun stop() {}
    }

    private class ScriptedPitchDetector(private val frequencies: List<Float>) : PitchDetector {
        private var index = 0
        override fun detect(samples: FloatArray): PitchResult? {
            val freq = frequencies[index.coerceAtMost(frequencies.size - 1)]
            index++
            return PitchResult(frequencyHz = freq, confidence = 0.95f)
        }
    }

    private fun shiftByCents(base: Float, cents: Float): Float =
        (base * 2.0.pow(cents / 1200.0)).toFloat()

    /** Runs all scripted frames through the engine, returns the state for the last frame. */
    private fun runFrames(engine: TunerEngine, frequencies: List<Float>): TunerState {
        val marker = frequencies.last()
        engine.startListening()
        return runBlocking {
            withTimeout(5_000) {
                engine.state.first { it.frequencyHz == marker }
            }
        }
    }

    private fun engineFor(frequencies: List<Float>): TunerEngine =
        TunerEngine(FakeAudioSource(frequencies.size), ScriptedPitchDetector(frequencies))

    private val e2 = 440f * 2f.pow((40 - 69) / 12f) // 82.407 Hz, string 6 in Standard

    @Test
    fun `centsOffset measures against target string, not nearest semitone`() {
        // D#2: exactly on a chromatic semitone but 100 cents flat of the E2 target.
        // The old chromatic centsOffset reported ~0 here (frozen ring, "+0c" readout).
        val dSharp2 = shiftByCents(e2, -100f)
        val frequencies = List(11) { dSharp2 } + shiftByCents(e2, -100.01f)

        val state = runFrames(engineFor(frequencies), frequencies)

        assertEquals("D#", state.noteName) // display note stays chromatic
        assertTrue(
            "centsOffset should be ~-100 (target-based), was ${state.centsOffset}",
            state.centsOffset < -50f
        )
        assertFalse("must not read in tune 100c below target", state.isInTune)
    }

    @Test
    fun `chromatic mode falls back to nearest semitone for cents`() {
        val dSharp2 = shiftByCents(e2, -100f)
        val frequencies = List(11) { dSharp2 } + shiftByCents(e2, -100.01f)

        val engine = engineFor(frequencies)
        engine.setTuning(TuningLibrary.CHROMATIC_MODE)
        val state = runFrames(engine, frequencies)

        assertEquals("D#", state.noteName)
        assertTrue(
            "chromatic cents should be ~0, was ${state.centsOffset}",
            abs(state.centsOffset) < 5f
        )
        assertTrue(state.isInTune)
    }

    @Test
    fun `isInTune uses the same smoothed cents as the readout`() {
        // Jitter alternating +/-8c around E2: raw frames are outside the +/-5c
        // tolerance, but the EMA-smoothed cents (what the readout shows) settle
        // near 0. Green state must follow the smoothed value, not raw frames.
        val frequencies = List(20) { i ->
            shiftByCents(e2, if (i % 2 == 0) 8f else -8f)
        } + shiftByCents(e2, 8.01f)

        val state = runFrames(engineFor(frequencies), frequencies)

        assertTrue(
            "smoothed cents should settle near 0, was ${state.centsOffset}",
            abs(state.centsOffset) < 5f
        )
        assertTrue("isInTune must follow smoothed cents", state.isInTune)
    }
}
