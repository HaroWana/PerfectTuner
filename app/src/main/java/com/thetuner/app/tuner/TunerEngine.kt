package com.thetuner.app.tuner

import com.thetuner.app.audio.AudioSource
import com.thetuner.app.detection.PitchDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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

    // Pitch-tracking state below is owned by the processing coroutine
    // (Dispatchers.Default) and must only be mutated there — see resetRequested.
    private val medianWindow = FloatArray(3)
    private var medianCount = 0
    private var silenceCounter = 0
    private val stringDetector = StringDetector()
    private var smoothedCents = 0f
    private var lastTargetFreq = 0f
    private var lastAcceptedFreq = 0f
    private var pendingJumpFreq = 0f
    private var pendingJumpCount = 0

    // @Volatile: written from main thread via setTuning/setA4Reference,
    // read from Dispatchers.Default in processFrame. Volatile ensures visibility.
    @Volatile private var activeTuning: GuitarTuning = STANDARD_TUNING
    @Volatile private var a4Reference: Float = 440f

    // setTuning cannot reset the tracking fields directly (it runs on the main
    // thread with no happens-before edge to the processing coroutine); it sets
    // this flag and processFrame performs the reset on its own thread.
    private val resetRequested = AtomicBoolean(false)

    private companion object {
        // One audio frame is 4096 samples at 44.1 kHz ≈ 93 ms
        const val SILENCE_DBFS_THRESHOLD = -70f
        const val CONTINUITY_CENTS = 150f
        const val JUMP_CONFIRM_FRAMES = 3 // ~280 ms to confirm a real pitch jump
        const val SILENCE_FRAME_COUNT = 15 // ~1.4 s of gated frames before showing silence
        const val IN_TUNE_TOLERANCE = 5.0f
        const val EMA_ALPHA = 0.2f
    }

    fun setTuning(tuning: GuitarTuning) {
        // Redundant calls (DataStore re-emits on every unrelated settings write)
        // must not wipe the string lock mid-tune
        if (tuning.id == activeTuning.id) return
        activeTuning = tuning
        resetRequested.set(true)
        // Update state immediately so UI reflects change even during silence
        _state.update { it.copy(activeTuningId = tuning.id) }
    }

    fun setA4Reference(hz: Float) {
        a4Reference = hz.coerceIn(430f, 450f)
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        listeningJob = newScope.launch {
            resetPitchTracking()
            silenceCounter = 0
            resetRequested.set(false) // superseded by the full reset above
            _state.value = TunerState(
                isListening = true,
                isSilent = true,
                activeTuningId = activeTuning.id
            )
            try {
                audioSource.frames().collect { frame ->
                    processFrame(frame)
                }
            } catch (e: CancellationException) {
                throw e // stop/restart must not overwrite the state written by stopListening
            } catch (e: Exception) {
                _state.value = TunerState(
                    isListening = false,
                    isSilent = true,
                    activeTuningId = activeTuning.id
                )
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        scope?.cancel()
        scope = null
        _state.value = TunerState(
            isListening = false,
            isSilent = true,
            activeTuningId = activeTuning.id
        )
    }

    private fun resetPitchTracking() {
        smoothedCents = 0f
        lastTargetFreq = 0f
        lastAcceptedFreq = 0f
        pendingJumpFreq = 0f
        pendingJumpCount = 0
        medianCount = 0
        stringDetector.reset()
    }

    private fun processFrame(frame: FloatArray) {
        if (resetRequested.compareAndSet(true, false)) {
            resetPitchTracking()
        }

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
        if (result == null) {
            silenceCounter++
            if (silenceCounter >= SILENCE_FRAME_COUNT) emitSilence()
            return
        }

        // Jump confirmation: YIN subharmonic errors (f/2 bursts observed on
        // device) arrive in 1-2 frame bursts and would drag the EMA toward
        // -1200c. Any pitch jump beyond CONTINUITY_CENTS must sustain for
        // JUMP_CONFIRM_FRAMES consecutive frames before it is followed; real
        // note changes pass in ~280 ms at the ~93 ms audio frame cadence.
        val freq = result.frequencyHz
        if (lastAcceptedFreq > 0f &&
            abs(NoteMapper.centsBetween(freq, lastAcceptedFreq)) > CONTINUITY_CENTS
        ) {
            if (pendingJumpFreq > 0f &&
                abs(NoteMapper.centsBetween(freq, pendingJumpFreq)) <= CONTINUITY_CENTS
            ) {
                pendingJumpCount++
            } else {
                pendingJumpFreq = freq
                pendingJumpCount = 1
            }
            if (pendingJumpCount < JUMP_CONFIRM_FRAMES) {
                silenceCounter++
                if (silenceCounter >= SILENCE_FRAME_COUNT) emitSilence()
                return
            }
            // Confirmed: drop stale old-pitch entries from the median window
            medianCount = 0
        }
        pendingJumpFreq = 0f
        pendingJumpCount = 0
        lastAcceptedFreq = freq

        silenceCounter = 0

        val filteredFreq = addToMedianFilter(result.frequencyHz)

        // NoteMapper always uses dynamic A4 reference
        val note = NoteMapper.frequencyToNote(filteredFreq, currentA4)

        // Chromatic mode: run string detection against Standard tuning for the visual overlay.
        // The note/cents display uses chromatic (NoteMapper already found nearest semitone).
        // CONTEXT.md: "string detection still runs — the nearest matching string lights up."
        val detectionFrequencies = if (isChromatic) {
            STANDARD_TUNING.frequencies(currentA4)
        } else {
            currentTuning.frequencies(currentA4)  // recompute each frame — do NOT cache (see RESEARCH.md pitfall 3)
        }
        val detectedString = stringDetector.detect(filteredFreq, detectionFrequencies)

        // Cents must be relative to the target string's frequency, not the nearest
        // chromatic note. Chromatic cents made green, rotation freeze, and the "+0c"
        // readout all fire on any semitone boundary, regardless of the tuning target.
        // In chromatic mode (or before a string locks): fall back to nearest note.
        val targetFreq = if (!isChromatic && detectedString != null) {
            detectionFrequencies[detectedString]
        } else {
            note.frequency
        }
        val rawCents = NoteMapper.centsBetween(filteredFreq, targetFreq)
        // Re-seed the EMA when the target changes — blending cents measured against
        // two different targets produces meaningless intermediate values.
        smoothedCents = if (targetFreq == lastTargetFreq) {
            EMA_ALPHA * rawCents + (1 - EMA_ALPHA) * smoothedCents
        } else {
            rawCents
        }
        lastTargetFreq = targetFreq
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
        resetPitchTracking()
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
