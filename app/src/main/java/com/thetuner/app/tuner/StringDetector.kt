package com.thetuner.app.tuner

import kotlin.math.abs

class StringDetector {

    private var currentStringIndex: Int? = null
    private var candidateIndex: Int? = null
    private var candidateCount: Int = 0

    /**
     * Detects which guitar string the given frequency is closest to.
     * Uses 3-frame hysteresis before switching to a new string.
     *
     * @param detectedFrequency The detected pitch frequency in Hz.
     * @param tuningFrequencies The target frequencies for each string.
     * @return The string index, or null if no string is close enough or hysteresis not yet met.
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

        // Reject if more than 200 cents from any string
        if (minCents > 200f) {
            return currentStringIndex
        }

        // If same as current string, no hysteresis needed
        if (closestIndex == currentStringIndex) {
            candidateIndex = null
            candidateCount = 0
            return currentStringIndex
        }

        // Apply 3-frame hysteresis for string switching
        if (closestIndex == candidateIndex) {
            candidateCount++
        } else {
            candidateIndex = closestIndex
            candidateCount = 1
        }

        if (candidateCount >= 3) {
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
