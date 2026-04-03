package com.thetuner.app.detection

data class PitchResult(
    val frequencyHz: Float,
    val confidence: Float, // 0.0 = no pitch, 1.0 = high confidence (inverted from YIN CMND)
    val timestamp: Long = System.nanoTime()
)
