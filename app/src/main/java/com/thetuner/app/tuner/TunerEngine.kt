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

    private val medianWindow = FloatArray(3)
    private var medianCount = 0
    private var silenceCounter = 0
    private val stringDetector = StringDetector()
    private var smoothedCents = 0f

    // @Volatile: written from main thread via setTuning/setA4Reference,
    // read from Dispatchers.Default in processFrame. Volatile ensures visibility.
    @Volatile private var activeTuning: GuitarTuning = STANDARD_TUNING
    @Volatile private var a4Reference: Float = 440f

    private companion object {
        const val SILENCE_DBFS_THRESHOLD = -50f
        const val CONFIDENCE_THRESHOLD = 0.80f
        const val SILENCE_FRAME_COUNT = 10
        const val IN_TUNE_TOLERANCE = 5.0f
        const val EMA_ALPHA = 0.2f
    }

    fun setTuning(tuning: GuitarTuning) {
        activeTuning = tuning
        // Reset hysteresis — stale state from old tuning causes wrong initial detection
        stringDetector.reset()
        smoothedCents = 0f
        medianCount = 0
        // Update state immediately so UI reflects change even during silence
        _state.value = _state.value.copy(activeTuningId = tuning.id)
    }

    fun setA4Reference(hz: Float) {
        a4Reference = hz.coerceIn(430f, 450f)
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
            _state.value = TunerState(
                isListening = true,
                isSilent = true,
                activeTuningId = activeTuning.id
            )
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
        _state.value = TunerState(
            isListening = false,
            isSilent = true,
            activeTuningId = activeTuning.id
        )
    }

    private fun processFrame(frame: FloatArray) {
        // Capture volatile fields once per frame to avoid races within the frame
        val currentTuning = activeTuning
        val currentA4 = a4Reference
        val isChromatic = currentTuning.strings.isEmpty()

        val rmsDbfs = computeRmsDbfs(frame)
        if (rmsDbfs < SILENCE_DBFS_THRESHOLD) {
            silenceCounter++
            if (silenceCounter >= SILENCE_FRAME_COUNT) emitSilence()
            return
        }

        val result = pitchDetector.detect(frame)
        if (result == null || result.confidence < CONFIDENCE_THRESHOLD) {
            silenceCounter++
            if (silenceCounter >= SILENCE_FRAME_COUNT) emitSilence()
            return
        }

        silenceCounter = 0

        val filteredFreq = addToMedianFilter(result.frequencyHz)

        // NoteMapper always uses dynamic A4 reference
        val note = NoteMapper.frequencyToNote(filteredFreq, currentA4)

        val rawCents = NoteMapper.centsBetween(filteredFreq, note.frequency)
        smoothedCents = EMA_ALPHA * rawCents + (1 - EMA_ALPHA) * smoothedCents

        // Chromatic mode: run string detection against Standard tuning for the visual overlay.
        // The note/cents display uses chromatic (NoteMapper already found nearest semitone).
        // CONTEXT.md: "string detection still runs — the nearest matching string lights up."
        val detectionFrequencies = if (isChromatic) {
            STANDARD_TUNING.frequencies(currentA4)
        } else {
            currentTuning.frequencies(currentA4)  // recompute each frame — do NOT cache (see RESEARCH.md pitfall 3)
        }
        val detectedString = stringDetector.detect(filteredFreq, detectionFrequencies)

        val isInTune = abs(smoothedCents) <= IN_TUNE_TOLERANCE

        _state.value = TunerState(
            noteName = note.name,
            octave = note.octave,
            frequencyHz = result.frequencyHz,
            isListening = true,
            isSilent = false,
            centsOffset = smoothedCents,
            detectedStringIndex = detectedString,
            isInTune = isInTune,
            activeTuningId = currentTuning.id
        )
    }

    private fun computeRmsDbfs(samples: FloatArray): Float {
        var sumSquares = 0.0
        for (sample in samples) sumSquares += sample * sample
        val rms = sqrt(sumSquares / samples.size).toFloat()
        return if (rms <= 0f) -100f else (20f * ln(rms.toDouble()) / ln(10.0)).toFloat()
    }

    private fun emitSilence() {
        _state.value = TunerState(
            isListening = true,
            isSilent = true,
            activeTuningId = activeTuning.id
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
