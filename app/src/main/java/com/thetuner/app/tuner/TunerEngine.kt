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
import kotlin.math.abs
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

    // String detection
    private val stringDetector = StringDetector()

    // EMA smoothing for cents offset
    private var smoothedCents = 0f

    // Silence threshold: -50 dBFS
    private companion object {
        const val SILENCE_DBFS_THRESHOLD = -50f
        const val CONFIDENCE_THRESHOLD = 0.80f
        const val SILENCE_FRAME_COUNT = 2 // ~186ms at 4096/44100
        const val IN_TUNE_TOLERANCE = 5.0f
        const val EMA_ALPHA = 0.2f
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return

        medianCount = 0
        silenceCounter = 0
        smoothedCents = 0f
        stringDetector.reset()

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        listeningJob = newScope.launch {
            _state.value = TunerState(isListening = true, isSilent = true)
            try {
                audioSource.frames().collect { frame ->
                    processFrame(frame)
                }
            } catch (e: Exception) {
                _state.value = TunerState(isListening = false, isSilent = true)
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
        smoothedCents = 0f
        stringDetector.reset()
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

        // Step 5: Compute cents offset with EMA smoothing
        val rawCents = NoteMapper.centsBetween(filteredFreq, note.frequency)
        smoothedCents = EMA_ALPHA * rawCents + (1 - EMA_ALPHA) * smoothedCents

        // Step 6: Detect string with hysteresis
        val detectedString = stringDetector.detect(filteredFreq, STANDARD_TUNING.frequencies())

        // Step 7: Determine in-tune state
        val isInTune = abs(smoothedCents) <= IN_TUNE_TOLERANCE

        _state.value = TunerState(
            noteName = note.name,
            octave = note.octave,
            frequencyHz = result.frequencyHz, // Raw value for display
            isListening = true,
            isSilent = false,
            centsOffset = smoothedCents,
            detectedStringIndex = detectedString,
            isInTune = isInTune
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
        smoothedCents = 0f
        stringDetector.reset()
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
