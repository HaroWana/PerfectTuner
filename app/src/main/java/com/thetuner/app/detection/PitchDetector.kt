package com.thetuner.app.detection

interface PitchDetector {
    fun detect(samples: FloatArray): PitchResult?
}
