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
    // No Compose state: Canvas reads this on every draw since phase changes trigger redraws.
    val workSamplesHolder = remember { arrayOfNulls<FloatArray>(1) }

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

                // --- Rotation speed (EMA-smoothed) ---
                // Target-based cents can reach ±200 (vs ±50 chromatic); cap the
                // exponential input so far-off strings spin at max speed, not e^16.
                val absCents = min(abs(currentCentsOffset), SPEED_CENTS_CAP)
                val targetSpeed = if (absCents > TOLERANCE_CENTS) {
                    -sign(currentCentsOffset) * SPEED_SCALE * (exp(EXPO_K * absCents) - 1f)
                } else {
                    0f
                }
                currentSpeed += (targetSpeed - currentSpeed) * SPEED_SMOOTH_ALPHA
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
                } else if (target != null && target.isNotEmpty()) {
                    val work = workSamplesHolder[0]
                    val frequencyChanged = prevWasSilent || stringIdx != prevStringIndex

                    if (work == null || work.size != target.size || frequencyChanged) {
                        // New session or string switch: copy immediately — never blend
                        // between waveforms from different frequencies (produces garbage)
                        workSamplesHolder[0] = target.copyOf()
                    } else {
                        // Same frequency: smooth blend toward the latest audio frame
                        for (i in work.indices) {
                            work[i] += (target[i] - work[i]) * WAVEFORM_BLEND_ALPHA
                        }
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
                val peak = samples.maxOf { abs(it) }
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
                val path = Path().apply { fillType = PathFillType.EvenOdd }

                for (i in 0 until SAMPLE_COUNT) {
                    val theta = (i.toDouble() / (SAMPLE_COUNT - 1)) * 2.0 * Math.PI
                    val base = startOffset + i * n / SAMPLE_COUNT

                    // 5-point box filter over adjacent mapped sample positions
                    val s0 = samples[((base - 2) % n + n) % n]
                    val s1 = samples[((base - 1) % n + n) % n]
                    val s2 = samples[base % n]
                    val s3 = samples[(base + 1) % n]
                    val s4 = samples[(base + 2) % n]
                    val smoothed = (s0 + s1 + s2 + s3 + s4) / 5f

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

private const val SPEED_SCALE = 3.35f
private const val EXPO_K = 0.08f
private const val SPEED_CENTS_CAP = 50f
private const val TOLERANCE_CENTS = 5f
private const val SAMPLE_COUNT = 361
private const val SPEED_SMOOTH_ALPHA = 0.06f
private const val SPEED_DEAD_ZONE = 0.5f
private const val WAVEFORM_BLEND_ALPHA = 0.4f
