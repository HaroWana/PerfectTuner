package com.thetuner.app.detection

import com.thetuner.app.audio.AudioConfig

class YinPitchDetector(
    private val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    private val threshold: Float = 0.15f,
    private val bufferSize: Int = AudioConfig.ANALYSIS_BUFFER_SIZE
) : PitchDetector {

    private val halfBuffer = bufferSize / 2
    private val yinBuffer = FloatArray(halfBuffer)

    override fun detect(samples: FloatArray): PitchResult? {
        if (samples.size < bufferSize) return null

        // Step 1: Difference function
        difference(samples)

        // Step 2: Cumulative mean normalized difference
        cumulativeMeanNormalizedDifference()

        // Step 3: Absolute threshold -- find first tau below threshold
        val tau = absoluteThreshold() ?: return null

        // Step 4: Parabolic interpolation for sub-sample accuracy
        val betterTau = parabolicInterpolation(tau)

        // Step 5: Convert to frequency
        return PitchResult(frequencyHz = sampleRate.toFloat() / betterTau)
    }

    private fun difference(buffer: FloatArray) {
        // tau=0 is skipped: cumulativeMeanNormalizedDifference overwrites it
        for (tau in 1 until halfBuffer) {
            yinBuffer[tau] = 0f
            for (i in 0 until halfBuffer) {
                val delta = buffer[i] - buffer[i + tau]
                yinBuffer[tau] += delta * delta
            }
        }
    }

    private fun cumulativeMeanNormalizedDifference() {
        yinBuffer[0] = 1f // Prevent division by zero; tau=0 is never a valid period
        var runningSum = 0f
        for (tau in 1 until halfBuffer) {
            runningSum += yinBuffer[tau]
            // Silence/DC input: all differences are zero — force CMND to 1 so no
            // tau passes the threshold (instead of relying on NaN comparisons)
            yinBuffer[tau] = if (runningSum == 0f) 1f else yinBuffer[tau] * tau / runningSum
        }
    }

    private fun absoluteThreshold(): Int? {
        // Find first tau where CMND drops below threshold
        var tau = 2
        while (tau < halfBuffer) {
            if (yinBuffer[tau] < threshold) {
                // Walk to local minimum in this dip
                while (tau + 1 < halfBuffer && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++
                }
                return tau
            }
            tau++
        }
        return null // No pitch detected
    }

    private fun parabolicInterpolation(tau: Int): Float {
        if (tau <= 0 || tau >= halfBuffer - 1) return tau.toFloat()

        val s0 = yinBuffer[tau - 1]
        val s1 = yinBuffer[tau]
        val s2 = yinBuffer[tau + 1]
        val denominator = 2f * (2f * s1 - s2 - s0)

        return if (denominator == 0f) {
            tau.toFloat()
        } else {
            tau + (s2 - s0) / denominator
        }
    }
}
