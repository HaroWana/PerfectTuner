package com.thetuner.app.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// Exposed for TunerScreen to pass to StringsOverlay
const val RING_RADIUS_DP = 136f

/**
 * Canvas-based strobe ring with real audio waveform rendering.
 *
 * Drawn as a filled annular shape:
 * - Inner boundary: perfect circle (flat, no waveform)
 * - Outer boundary: follows the audio waveform, bulging outward
 *
 * Smoothness:
 * - Rotation speed is EMA-smoothed to prevent jitter from noisy centsOffset.
 * - Waveform samples are blended frame-by-frame toward the latest audio frame.
 * - Outer edge uses a 5-point moving average over adjacent samples to reduce
 *   high-frequency noise (jaggedness) while preserving the overall waveform shape.
 *
 * Frequency transitions:
 * - When detectedStringIndex changes, the work buffer is reset immediately to the
 *   incoming samples — no blend between incompatible waveform patterns from different
 *   frequencies. StringDetector's hysteresis ensures this only fires on genuine switches.
 * - Transitioning from silence to active also resets the buffer.
 *
 * Direction convention:
 * - Flat pitch (negative cents) -> clockwise rotation
 * - Sharp pitch (positive cents) -> counterclockwise rotation
 */
@Composable
fun StrobeRing(
    centsOffset: Float,
    ringColor: Color,
    isSilent: Boolean,
    waveformSamples: FloatArray?,
    detectedStringIndex: Int?,
    modifier: Modifier = Modifier
) {
    val phase = remember { mutableFloatStateOf(0f) }
    val currentCentsOffset by rememberUpdatedState(centsOffset)
    val currentWaveform by rememberUpdatedState(waveformSamples)
    val currentStringIndex by rememberUpdatedState(detectedStringIndex)
    val currentIsSilent by rememberUpdatedState(isSilent)

    // In-place working buffer — blended toward latest audio frame each display frame.
    // Not Compose state: redraws are driven by phase writes while rotating, and by
    // recomposition when each new waveformSamples array arrives (also every active
    // frame), which covers the frozen-in-tune case where phase stops updating.
    val workSamplesHolder = remember { arrayOfNulls<FloatArray>(1) }
    val smoothedPeakHolder = remember { floatArrayOf(0f) }
    val ringPath = remember { Path() }

    LaunchedEffect(Unit) {
        var prevFrameTime = 0L
        var currentSpeed = 0f    // signed degrees/second, EMA-smoothed
        var prevStringIndex: Int? = -999  // sentinel: force reset on first active frame
        var prevWasSilent = true

        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                val deltaSeconds = if (prevFrameTime == 0L) 0f
                    else (frameTimeMs - prevFrameTime) / 1000f
                prevFrameTime = frameTimeMs

                // Alphas derived from per-second rates so smoothing feels identical
                // on 60 Hz and 120 Hz displays (per-frame constants settle twice as
                // fast at 120 Hz).
                val speedAlpha = 1f - exp(-SPEED_SMOOTH_RATE * deltaSeconds)
                val blendAlpha = 1f - exp(-WAVEFORM_BLEND_RATE * deltaSeconds)

                // --- Rotation speed (EMA-smoothed) ---
                // Target-based cents can reach ±200 (vs ±50 chromatic); cap the
                // exponential input so far-off strings spin at max speed, not e^16.
                val absCents = min(abs(currentCentsOffset), SPEED_CENTS_CAP)
                val targetSpeed = if (absCents > TOLERANCE_CENTS) {
                    -sign(currentCentsOffset) * SPEED_SCALE * (exp(EXPO_K * absCents) - 1f)
                } else {
                    0f
                }
                currentSpeed += (targetSpeed - currentSpeed) * speedAlpha
                if (abs(currentSpeed) > SPEED_DEAD_ZONE) {
                    phase.floatValue = (phase.floatValue + currentSpeed * deltaSeconds) % 360f
                }

                // --- Waveform buffer management ---
                val silent = currentIsSilent
                val stringIdx = currentStringIndex
                val target = currentWaveform

                if (silent) {
                    // Going silent: clear buffer so next active frame starts fresh
                    workSamplesHolder[0] = null
                    smoothedPeakHolder[0] = 0f
                } else if (target != null && target.isNotEmpty()) {
                    val work = workSamplesHolder[0]
                    val frequencyChanged = prevWasSilent || stringIdx != prevStringIndex

                    if (work == null || work.size != target.size || frequencyChanged) {
                        // New session or string switch: copy immediately — never blend
                        // between waveforms from different frequencies (produces garbage)
                        workSamplesHolder[0] = target.copyOf()
                        smoothedPeakHolder[0] = peakOf(target)
                    } else {
                        // Same frequency: smooth blend toward the latest audio frame
                        for (i in work.indices) {
                            work[i] += (target[i] - work[i]) * blendAlpha
                        }
                        // Smooth the normalization peak too — an instantaneous 1/peak
                        // rescales the whole ring abruptly on one-frame transients
                        smoothedPeakHolder[0] += (peakOf(work) - smoothedPeakHolder[0]) * blendAlpha
                    }
                }

                prevStringIndex = stringIdx
                prevWasSilent = silent
            }
        }
    }

    Canvas(modifier = modifier) {
        val ringDiameter = size.minDimension * 0.85f
        val ringRadius = ringDiameter / 2f
        val baseThickness = ringDiameter * 0.06f
        val waveAmplitude = baseThickness * 1.2f

        if (isSilent) {
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = ringRadius + baseThickness / 2f,
                center = center,
                style = Stroke(width = baseThickness)
            )
        } else {
            val samples = workSamplesHolder[0]
            if (samples != null && samples.isNotEmpty()) {
                val peak = smoothedPeakHolder[0]
                val scale = if (peak > 0.001f) 1f / peak else 0f
                val n = samples.size

                val startOffset = ((phase.floatValue / 360f) * n).toInt().let {
                    ((it % n) + n) % n
                }

                // Filled annular path: flat inner circle, waveform outer edge.
                // Outer edge uses a 5-point moving average over adjacent samples to
                // smooth out high-frequency noise without losing the waveform shape.
                // Sample mapped [−1,1] → [0,1] so the outer edge always stays outside
                // the inner circle and the full waveform asymmetry is preserved.
                val path = ringPath.apply {
                    reset()
                    fillType = PathFillType.EvenOdd
                }
                var seamStartValue = 0f

                for (i in 0 until SAMPLE_COUNT) {
                    val theta = (i.toDouble() / (SAMPLE_COUNT - 1)) * 2.0 * Math.PI
                    val base = startOffset + i * n / SAMPLE_COUNT

                    // 5-point box filter over adjacent mapped sample positions
                    val s0 = samples[((base - 2) % n + n) % n]
                    val s1 = samples[((base - 1) % n + n) % n]
                    val s2 = samples[base % n]
                    val s3 = samples[(base + 1) % n]
                    val s4 = samples[(base + 2) % n]
                    var smoothed = (s0 + s1 + s2 + s3 + s4) / 5f

                    // First and last points share θ=0 but map to different samples;
                    // blend the tail toward the starting value to close the seam
                    if (i == 0) {
                        seamStartValue = smoothed
                    } else if (i > SAMPLE_COUNT - 1 - SEAM_BLEND_DEGREES) {
                        val t = (i - (SAMPLE_COUNT - 1 - SEAM_BLEND_DEGREES)).toFloat() /
                            SEAM_BLEND_DEGREES
                        smoothed += (seamStartValue - smoothed) * t
                    }

                    val normalised = (smoothed * scale + 1f) * 0.5f  // [0, 1]
                    val r = (ringRadius + baseThickness + normalised * waveAmplitude).toFloat()
                    val x = (center.x + r * cos(theta)).toFloat()
                    val y = (center.y + r * sin(theta)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                // Inner perfect circle — EvenOdd punches it out
                path.addOval(
                    Rect(
                        left = center.x - ringRadius,
                        top = center.y - ringRadius,
                        right = center.x + ringRadius,
                        bottom = center.y + ringRadius
                    )
                )

                drawPath(path = path, color = ringColor)
            } else {
                drawCircle(
                    color = ringColor.copy(alpha = 0.4f),
                    radius = ringRadius + baseThickness / 2f,
                    center = center,
                    style = Stroke(width = baseThickness)
                )
            }
        }
    }
}

private fun peakOf(samples: FloatArray): Float {
    var peak = 0f
    for (s in samples) {
        val a = abs(s)
        if (a > peak) peak = a
    }
    return peak
}

private const val SPEED_SCALE = 3.35f
private const val EXPO_K = 0.08f
private const val SPEED_CENTS_CAP = 50f
private const val TOLERANCE_CENTS = 5f
private const val SAMPLE_COUNT = 361
private const val SEAM_BLEND_DEGREES = 12
private const val SPEED_DEAD_ZONE = 0.5f

// Per-second EMA rates; alpha = 1 - exp(-rate * dt). Chosen to match the feel of
// the previous per-frame alphas (0.06 and 0.4) at 60 Hz.
private const val SPEED_SMOOTH_RATE = 3.7f
private const val WAVEFORM_BLEND_RATE = 30f
