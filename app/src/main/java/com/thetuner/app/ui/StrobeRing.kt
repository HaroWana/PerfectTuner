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
 * The phase offset shifts which sample maps to angle 0, producing visible rotation.
 * Speed uses exponential decay and is EMA-smoothed to prevent jitter.
 * Waveform samples are blended frame-by-frame to smooth shape transitions.
 * Stops completely when within +/-5 cents. Dim static circle when silent.
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
    modifier: Modifier = Modifier
) {
    val phase = remember { mutableFloatStateOf(0f) }
    val currentCentsOffset by rememberUpdatedState(centsOffset)
    val currentWaveform by rememberUpdatedState(waveformSamples)

    // In-place working buffer — blended toward latest audio frame each display frame.
    // No Compose state: Canvas reads this on every draw since phase changes trigger redraws.
    val workSamplesHolder = remember { arrayOfNulls<FloatArray>(1) }

    LaunchedEffect(Unit) {
        var prevFrameTime = 0L
        var currentSpeed = 0f  // signed degrees/second, EMA-smoothed

        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                val deltaSeconds = if (prevFrameTime == 0L) 0f
                    else (frameTimeMs - prevFrameTime) / 1000f
                prevFrameTime = frameTimeMs

                // EMA-smooth rotation speed to prevent noisy centsOffset causing lurching
                val absCents = abs(currentCentsOffset)
                val targetSpeed = if (absCents > TOLERANCE_CENTS) {
                    -sign(currentCentsOffset) * SPEED_SCALE * (exp(EXPO_K * absCents) - 1f)
                } else {
                    0f
                }
                currentSpeed += (targetSpeed - currentSpeed) * SPEED_SMOOTH_ALPHA
                if (abs(currentSpeed) > SPEED_DEAD_ZONE) {
                    phase.floatValue = (phase.floatValue + currentSpeed * deltaSeconds) % 360f
                }

                // Blend working sample buffer toward latest audio frame — avoids shape jumps
                // at audio frame rate (~40 Hz) by spreading the transition over display frames
                val target = currentWaveform
                if (target != null && target.isNotEmpty()) {
                    val work = workSamplesHolder[0]
                    if (work == null || work.size != target.size) {
                        workSamplesHolder[0] = target.copyOf()
                    } else {
                        for (i in work.indices) {
                            work[i] += (target[i] - work[i]) * WAVEFORM_BLEND_ALPHA
                        }
                    }
                } else {
                    workSamplesHolder[0] = null
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val ringDiameter = size.minDimension * 0.85f
        val ringRadius = ringDiameter / 2f
        val baseThickness = ringDiameter * 0.06f   // minimum ring width (inner to outer base)
        val waveAmplitude = baseThickness * 1.2f    // max outer-edge excursion beyond base

        if (isSilent) {
            // Dim neutral circle — signals "listening" state
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = ringRadius + baseThickness / 2f,
                center = center,
                style = Stroke(width = baseThickness)
            )
        } else {
            val samples = workSamplesHolder[0]
            if (samples != null && samples.isNotEmpty()) {
                // Peak-normalize so the waveform always uses the full amplitude range
                val peak = samples.maxOf { abs(it) }
                val scale = if (peak > 0.001f) 1f / peak else 0f

                // Which sample aligns with angle 0 — advancing this is the rotation mechanism
                val startOffset = ((phase.floatValue / 360f) * samples.size).toInt().let {
                    ((it % samples.size) + samples.size) % samples.size
                }

                // Filled annular path:
                //   Inner boundary — perfect circle at ringRadius (inner edge, flat)
                //   Outer boundary — waveform-displaced, always >= ringRadius + baseThickness
                //
                // Sample mapped to [0, waveAmplitude] via (s * scale + 1) / 2 so the outer
                // edge varies smoothly between baseThickness and baseThickness + waveAmplitude
                // while preserving the true waveform shape (not rectified).
                val path = Path().apply { fillType = PathFillType.EvenOdd }

                // Outer waveform edge
                for (i in 0 until SAMPLE_COUNT) {
                    val theta = (i.toDouble() / (SAMPLE_COUNT - 1)) * 2.0 * Math.PI
                    val sampleIdx = (startOffset + i * samples.size / SAMPLE_COUNT) % samples.size
                    val normalised = (samples[sampleIdx] * scale + 1f) * 0.5f  // [0, 1]
                    val r = (ringRadius + baseThickness + normalised * waveAmplitude).toFloat()
                    val x = (center.x + r * cos(theta)).toFloat()
                    val y = (center.y + r * sin(theta)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                // Inner circle — EvenOdd punches it out, leaving a flat circular hole
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
                // Fallback: no samples yet
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
private const val TOLERANCE_CENTS = 5f
private const val SAMPLE_COUNT = 361
private const val SPEED_SMOOTH_ALPHA = 0.06f
private const val SPEED_DEAD_ZONE = 0.5f
private const val WAVEFORM_BLEND_ALPHA = 0.4f
