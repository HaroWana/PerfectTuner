package com.thetuner.app.tuner

import kotlin.math.abs

class StringDetector {

    private var currentStringIndex: Int? = null
    private var candidateIndex: Int? = null
    private var candidateCount: Int = 0

    private companion object {
        // LOCK_RADIUS_CENTS must stay above TunerEngine.CONTINUITY_CENTS (150c):
        // pitches the engine accepts as continuous should still map to a string.
        const val LOCK_RADIUS_CENTS = 200f
        const val HYSTERESIS_FRAMES = 3
    }

    /**
     * Detects which guitar string the given frequency is closest to.
     *
     * Switching to a new string requires [HYSTERESIS_FRAMES] consecutive frames
     * on that string. Once locked, the lock is held even while the pitch drifts
     * out of range (re-lock hold) — only [reset] or a confirmed switch changes it.
     * For tunings with unison strings the lowest matching index wins; pitch alone
     * cannot tell unison courses apart.
     *
     * @param detectedFrequency The detected pitch frequency in Hz.
     * @param tuningFrequencies The target frequencies for each string.
     * @return The locked string index, or null before the first lock is confirmed.
     */
    fun detect(detectedFrequency: Float, tuningFrequencies: List<Float>): Int? {
        // Find closest string by cent distance
        var minCents = Float.MAX_VALUE
        var closestIndex = -1

        for (i in tuningFrequencies.indices) {
            val cents = abs(NoteMapper.centsBetween(detectedFrequency, tuningFrequencies[i]))
            if (cents < minCents) {
                minCents = cents
                closestIndex = i
            }
        }

        // Out of range of every string: hold the current lock, but clear any
        // pending switch — hysteresis counts consecutive frames only
        if (minCents > LOCK_RADIUS_CENTS) {
            candidateIndex = null
            candidateCount = 0
            return currentStringIndex
        }

        // If same as current string, no hysteresis needed
        if (closestIndex == currentStringIndex) {
            candidateIndex = null
            candidateCount = 0
            return currentStringIndex
        }

        // Apply hysteresis for string switching
        if (closestIndex == candidateIndex) {
            candidateCount++
        } else {
            candidateIndex = closestIndex
            candidateCount = 1
        }

        if (candidateCount >= HYSTERESIS_FRAMES) {
            currentStringIndex = candidateIndex
            candidateIndex = null
            candidateCount = 0
            return currentStringIndex
        }

        return currentStringIndex
    }

    /**
     * Resets all detection state.
     */
    fun reset() {
        currentStringIndex = null
        candidateIndex = null
        candidateCount = 0
    }
}
