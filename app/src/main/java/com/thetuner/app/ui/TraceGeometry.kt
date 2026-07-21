package com.thetuner.app.ui

data class TraceSample(
    val timeMs: Long,
    val cents: Float?, // null = pen lifted (silence)
    val inTune: Boolean,
    val stringIndex: Int?
)

object TraceGeometry {
    const val WINDOW_MS = 5000L
    const val CENTS_RANGE = 50f

    fun centsToX(cents: Float, width: Float, inset: Float): Float {
        val clamped = cents.coerceIn(-CENTS_RANGE, CENTS_RANGE)
        return width / 2f + (clamped / CENTS_RANGE) * (width / 2f - inset)
    }

    fun timeToY(timeMs: Long, nowMs: Long, height: Float): Float {
        return (nowMs - timeMs).toFloat() / WINDOW_MS * height
    }
}
