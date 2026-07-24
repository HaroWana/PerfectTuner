package com.thetuner.app.tuner

import com.thetuner.app.audio.AudioSource
import com.thetuner.app.detection.PitchDetector
import com.thetuner.app.detection.PitchResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class TunerEngineTest {

    private class FakeAudioSource(
        private val amplitudes: List<Float>
    ) : AudioSource {
        // 0.1 amplitude -> -20 dBFS, well above the silence gate
        constructor(frameCount: Int, amplitude: Float = 0.1f) : this(List(frameCount) { amplitude })

        override fun frames(): Flow<FloatArray> = flow {
            for (amplitude in amplitudes) emit(FloatArray(1024) { amplitude })
        }
    }

    private class ScriptedPitchDetector(private val results: List<PitchResult>) : PitchDetector {
        private var index = 0
        override fun detect(samples: FloatArray): PitchResult? {
            val result = results[index.coerceAtMost(results.size - 1)]
            index++
            return result
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

    private fun scripted(frequencies: List<Float>) =
        ScriptedPitchDetector(frequencies.map { PitchResult(frequencyHz = it) })

    private fun engineFor(frequencies: List<Float>, amplitude: Float = 0.1f): TunerEngine =
        TunerEngine(FakeAudioSource(frequencies.size, amplitude), scripted(frequencies))

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
    fun `quiet decaying signal is still tracked`() {
        // 0.0006 amplitude ~= -64 dBFS: deep into a pluck's decay. It must
        // pass the silence gate and keep producing pitch states.
        val frequencies = List(11) { e2 } + shiftByCents(e2, 0.01f)

        val state = runFrames(engineFor(frequencies, amplitude = 0.0006f), frequencies)

        assertEquals("E", state.noteName)
        assertFalse("quiet frames must not be treated as silence", state.isSilent)
    }

    @Test
    fun `octave-error burst does not drag the cents offset`() {
        // YIN subharmonic error observed on device: while tracking E2, 1-2 frame
        // bursts of f/2. They must be rejected as detection noise, not fed to
        // the EMA (which would slam the display toward -1200c).
        val marker = shiftByCents(e2, 0.01f)
        val script = List(6) { PitchResult(e2) } +
            List(2) { PitchResult(e2 / 2f) } +
            PitchResult(marker)
        val engine = TunerEngine(FakeAudioSource(script.size), ScriptedPitchDetector(script))

        engine.startListening()
        val state = runBlocking {
            withTimeout(5_000) { engine.state.first { it.frequencyHz == marker } }
        }

        assertTrue(
            "octave-error burst must not drag centsOffset, was ${state.centsOffset}",
            abs(state.centsOffset) < 10f
        )
        assertEquals("E", state.noteName)
    }

    @Test
    fun `sustained pitch change is followed after confirmation`() {
        // A real note change sustains: after 3 consistent frames at the new
        // pitch the engine must follow it (unlike 1-2 frame error bursts).
        val a2 = 110f
        val marker = shiftByCents(a2, 0.01f)
        val script = List(6) { PitchResult(e2) } +
            List(8) { PitchResult(a2) } +
            PitchResult(marker)
        val engine = TunerEngine(FakeAudioSource(script.size), ScriptedPitchDetector(script))

        engine.startListening()
        val state = runBlocking {
            withTimeout(5_000) { engine.state.first { it.frequencyHz == marker } }
        }

        assertEquals("A", state.noteName)
        assertTrue(
            "engine must track the confirmed new pitch, was ${state.centsOffset}",
            abs(state.centsOffset) < 10f
        )
    }

    @Test
    fun `brief detection dropouts do not read as silence`() {
        // 5 tracked frames, then 12 sub-gate frames (~1.1 s at the ~93 ms audio
        // frame cadence): a dropout in a decaying note must not declare silence.
        val marker = shiftByCents(e2, 0.02f)
        val frequencies = List(4) { e2 } + marker
        val amplitudes = List(5) { 0.1f } + List(12) { 0.0001f } // -80 dBFS, below the gate
        val engine = TunerEngine(FakeAudioSource(amplitudes), scripted(frequencies))

        engine.startListening()
        val silentState = runBlocking {
            withTimeout(5_000) { engine.state.first { it.frequencyHz == marker } }
            withTimeoutOrNull(500) { engine.state.first { it.isSilent } }
        }

        assertNull("12 gated frames must not flip the state to silent", silentState)
    }

    /** Audio source the test drives frame by frame, so engine calls can be interleaved deterministically. */
    private class ChannelAudioSource : AudioSource {
        val channel = Channel<FloatArray>(Channel.UNLIMITED)
        override fun frames(): Flow<FloatArray> = channel.consumeAsFlow()
        fun sendFrame() {
            channel.trySend(FloatArray(1024) { 0.1f })
        }
    }

    @Test
    fun `setTuning with the same tuning does not reset the string lock`() = runBlocking {
        // Every DataStore edit re-emits the active tuning id; a redundant
        // setTuning call must not wipe the string lock mid-tune.
        val marker = shiftByCents(e2, 0.01f)
        val audio = ChannelAudioSource()
        val engine = TunerEngine(audio, scripted(listOf(e2, e2, e2, marker)))

        engine.startListening()
        repeat(3) { audio.sendFrame() }
        withTimeout(5_000) { engine.state.first { it.detectedStringIndex == 0 } }

        engine.setTuning(STANDARD_TUNING) // same tuning as the active one
        audio.sendFrame()
        val state = withTimeout(5_000) { engine.state.first { it.frequencyHz == marker } }
        engine.stopListening()

        assertEquals("string lock must survive a redundant setTuning", 0, state.detectedStringIndex)
    }

    @Test
    fun `setTuning with a different tuning resets the string lock`() = runBlocking {
        val marker = shiftByCents(e2, 0.01f)
        val audio = ChannelAudioSource()
        val engine = TunerEngine(audio, scripted(listOf(e2, e2, e2, marker)))

        engine.startListening()
        repeat(3) { audio.sendFrame() }
        withTimeout(5_000) { engine.state.first { it.detectedStringIndex == 0 } }

        engine.setTuning(TuningLibrary.findById("drop_d"))
        audio.sendFrame()
        val state = withTimeout(5_000) { engine.state.first { it.frequencyHz == marker } }
        engine.stopListening()

        assertEquals("drop_d", state.activeTuningId)
        assertNull("hysteresis must restart against the new tuning", state.detectedStringIndex)
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
