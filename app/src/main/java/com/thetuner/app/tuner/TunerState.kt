package com.thetuner.app.tuner

data class TunerState(
    val noteName: String? = null,
    val octave: Int? = null,
    val frequencyHz: Float = 0f,
    val isListening: Boolean = false,
    val isSilent: Boolean = true,
    val centsOffset: Float = 0f,
    val detectedStringIndex: Int? = null,
    val isInTune: Boolean = false,
    val activeTuningId: String = "standard",
    // Reference equality on purpose: a fresh array per frame keeps every emission
    // distinct so StateFlow never conflates consecutive active frames. Don't compare
    // TunerState instances expecting waveform content equality.
    val waveformSamples: FloatArray? = null
)
