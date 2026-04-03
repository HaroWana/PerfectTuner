package com.thetuner.app.tuner

import com.thetuner.app.audio.AudioSource
import com.thetuner.app.detection.PitchDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.sqrt

@Singleton
class TunerEngine @Inject constructor(
    private val audioSource: AudioSource,
    private val pitchDetector: PitchDetector
) {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private var listeningJob: Job? = null
    private var scope: CoroutineScope? = null

    // Pre-allocated median filter window (3 frames)
    private val medianWindow = FloatArray(3)
    private var medianCount = 0

    // Silence counter
    private var silenceCounter = 0

    // Silence threshold: -50 dBFS
    private companion object {
        const val SILENCE_DBFS_THRESHOLD = -50f
        const val CONFIDENCE_THRESHOLD = 0.80f
        const val SILENCE_FRAME_COUNT = 2 // ~186ms at 4096/44100
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return

        medianCount = 0
        silenceCounter = 0

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        listeningJob = newScope.launch {
            _state.value = TunerState(isListening = true, isSilent = true)

            audioSource.frames().collect { frame ->
                processFrame(frame)
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        scope?.cancel()
        scope = null
        medianCount = 0
        silenceCounter = 0
        _state.value = TunerState(isListening = false, isSilent = true)
    }

    private fun processFrame(frame: FloatArray) {
        // Step 1: Check signal presence via RMS amplitude in dBFS
        val rmsDbfs = computeRmsDbfs(frame)
        if (rmsDbfs < SILENCE_DBFS_THRESHOLD) {
            silenceCounter++
            if (silenceCounter >= SILENCE_FRAME_COUNT) {
                emitSilence()
            }
            return
        }

        // Step 2: Run pitch detection
        val result = pitchDetector.detect(frame)
        if (result == null || result.confidence < CONFIDENCE_THRESHOLD) {
            silenceCounter++
            if (silenceCounter >= SILENCE_FRAME_COUNT) {
                emitSilence()
            }
            return
        }

        // Valid detection -- reset silence counter
        silenceCounter = 0

        // Step 3: Apply 3-frame median filter for note stability
        val filteredFreq = addToMedianFilter(result.frequencyHz)

        // Step 4: Map to note
        val note = NoteMapper.frequencyToNote(filteredFreq)

        _state.value = TunerState(
            noteName = note.name,
            octave = note.octave,
            frequencyHz = result.frequencyHz, // Raw value for display
            isListening = true,
            isSilent = false
        )
    }

    private fun computeRmsDbfs(samples: FloatArray): Float {
        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / samples.size).toFloat()
        return if (rms <= 0f) -100f else (20f * ln(rms.toDouble()) / ln(10.0)).toFloat()
    }

    private fun emitSilence() {
        _state.value = TunerState(
            isListening = true,
            isSilent = true
        )
        medianCount = 0
    }

    private fun addToMedianFilter(frequency: Float): Float {
        if (medianCount < 3) {
            medianWindow[medianCount] = frequency
            medianCount++
        } else {
            // Shift window
            medianWindow[0] = medianWindow[1]
            medianWindow[1] = medianWindow[2]
            medianWindow[2] = frequency
        }

        return when (medianCount) {
            1 -> medianWindow[0]
            2 -> (medianWindow[0] + medianWindow[1]) / 2f
            else -> median3(medianWindow[0], medianWindow[1], medianWindow[2])
        }
    }

    private fun median3(a: Float, b: Float, c: Float): Float {
        return if (a <= b) {
            if (b <= c) b else if (a <= c) c else a
        } else {
            if (a <= c) a else if (b <= c) c else b
        }
    }
}
