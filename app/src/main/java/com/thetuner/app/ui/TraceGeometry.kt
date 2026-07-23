package com.thetuner.app.ui

data class TraceSample(
    val timeMs: Long,
    val cents: Float?, // null = pen lifted (silence)
    val inTune: Boolean,
    val stringIndex: Int?
)

data class SegmentRange(
    val start: Int, // overlaps previous run's last index when runs are contiguous
    val endExclusive: Int,
    val inTune: Boolean,
    val stringIndex: Int?
)

/**
 * Decides what the pen writes each frame. During silence the pen holds its
 * last value (a straight line) instead of lifting; it lifts only before the
 * first note and to break the line when a different string starts after a
 * held stretch.
 */
class PenTracker {

    private var heldCents: Float? = null
    private var heldInTune = false
    private var heldStringIndex: Int? = null
    private var holding = false

    fun samplesFor(
        timeMs: Long,
        isSilent: Boolean,
        cents: Float,
        inTune: Boolean,
        stringIndex: Int?,
        smoothingAlpha: Float = 1f
    ): List<TraceSample> {
        if (isSilent) {
            val held = heldCents
            holding = held != null
            return listOf(
                if (held != null) TraceSample(timeMs, held, heldInTune, heldStringIndex)
                else TraceSample(timeMs, null, false, null)
            )
        }
        val breakLine = holding && stringIndex != heldStringIndex
        val previous = heldCents
        // Snap on the first note and on string changes — gliding across strings
        // would draw a misleading sweep. Glide toward the live pitch otherwise.
        val smoothed = if (previous == null || stringIndex != heldStringIndex) {
            cents
        } else {
            previous + (cents - previous) * smoothingAlpha
        }
        heldCents = smoothed
        heldInTune = inTune
        heldStringIndex = stringIndex
        holding = false
        val sample = TraceSample(timeMs, smoothed, inTune, stringIndex)
        return if (breakLine) {
            listOf(TraceSample(timeMs, null, false, null), sample)
        } else {
            listOf(sample)
        }
    }

    fun reset() {
        heldCents = null
        heldInTune = false
        heldStringIndex = null
        holding = false
    }
}

object TraceGeometry {
    const val WINDOW_MS = 5000L
    const val CENTS_RANGE = 50f

    fun centsToX(cents: Float, width: Float, inset: Float): Float {
        val clamped = cents.coerceIn(-CENTS_RANGE, CENTS_RANGE)
        return width / 2f + (clamped / CENTS_RANGE) * (width / 2f - inset)
    }

    fun timeToY(timeMs: Long, nowMs: Long, height: Float, penY: Float = 0f): Float {
        return penY + (nowMs - timeMs).toFloat() / WINDOW_MS * (height - penY)
    }

    fun segment(samples: List<TraceSample>): List<SegmentRange> {
        val ranges = mutableListOf<SegmentRange>()
        var i = 0
        while (i < samples.size) {
            if (samples[i].cents == null) {
                i++
                continue
            }
            var j = i + 1
            while (j < samples.size &&
                samples[j].cents != null &&
                samples[j].inTune == samples[i].inTune &&
                samples[j].stringIndex == samples[i].stringIndex
            ) {
                j++
            }
            // Borrow the previous drawable point so contiguous runs connect visually
            val start = if (i > 0 && samples[i - 1].cents != null) i - 1 else i
            if (j - start >= 2) {
                ranges.add(SegmentRange(start, j, samples[i].inTune, samples[i].stringIndex))
            }
            i = j
        }
        return ranges
    }

    fun evictExpired(samples: ArrayDeque<TraceSample>, nowMs: Long) {
        val cutoff = nowMs - WINDOW_MS
        // Keep one sample past the cutoff so the trace exits the bottom edge smoothly
        while (samples.size >= 2 && samples[1].timeMs < cutoff) {
            samples.removeFirst()
        }
    }
}
