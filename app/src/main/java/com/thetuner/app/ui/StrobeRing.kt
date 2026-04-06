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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

// Exposed for TunerScreen to pass to StringsOverlay
const val RING_RADIUS_DP = 136f

/**
 * Canvas-based strobe ring with real audio waveform rendering.
 *
 * Renders the actual audio waveform around a ring, with a phase offset that advances
 * continuously based on cents deviation — this drives the rotation effect.
 * Speed uses exponential decay so motion slows naturally as pitch nears in-tune.
 * Stops completely when within +/-5 cents. Shows a dim static circle when silent
 * or when no waveform samples are available yet.
 *
 * Smoothing:
 * - Rotation speed is EMA-smoothed so noisy centsOffset doesn't cause erratic lurching.
 * - Waveform samples are blended frame-by-frame toward the latest audio frame so shape
 *   transitions are gradual rather than instant jumps at audio frame boundaries (~40 Hz).
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
    // rememberUpdatedState so the LaunchedEffect always reads the latest values
    // without restarting the coroutine on every recomposition
    val currentCentsOffset by rememberUpdatedState(centsOffset)
    val currentWaveform by rememberUpdatedState(waveformSamples)

    // In-place working buffer for waveform blending — no Compose state, no allocation per frame.
    // Canvas reads this on every draw since phase changes trigger redraws each display frame.
    val workSamplesHolder = remember { arrayOfNulls<FloatArray>(1) }

    // Frame-driven phase accumulator + waveform blend
    LaunchedEffect(Unit) {
        var prevFrameTime = 0L
        var currentSpeed = 0f  // signed degrees/second, smoothed

        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                val deltaSeconds = if (prevFrameTime == 0L) 0f
                    else (frameTimeMs - prevFrameTime) / 1000f
                prevFrameTime = frameTimeMs

                // --- Rotation speed smoothing ---
                // Compute target speed from current cents, then EMA-smooth it.
                // This prevents rapid centsOffset fluctuations from causing erratic lurching.
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

                // --- Waveform blending ---
                // Each display frame, lerp the working buffer toward the latest audio frame.
                // Audio arrives at ~40 Hz; display runs at ~60 Hz. Without blending, every
                // incoming audio frame causes an instant shape jump visible as stutter.
                val target = currentWaveform
                if (target != null && target.isNotEmpty()) {
                    val work = workSamplesHolder[0]
                    if (work == null || work.size != target.size) {
                        // First frame or size change — copy directly, no blend
                        workSamplesHolder[0] = target.copyOf()
                    } else {
                        // Blend in place: no allocation, thread-safe (both on main thread)
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
        val strokeWidth = ringDiameter * 0.06f
        val ringRadius = ringDiameter / 2f
        val waveAmplitude = ringRadius * 0.08f

        if (isSilent) {
            // Dim neutral circle — signals "listening" state
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
        } else {
            val samples = workSamplesHolder[0]
            if (samples != null && samples.isNotEmpty()) {
                // Normalize samples to their frame peak so the waveform always uses
                // full amplitude regardless of microphone level.
                val peak = samples.maxOf { kotlin.math.abs(it) }
                val scale = if (peak > 0.001f) 1f / peak else 0f

                // Render audio waveform around the ring.
                // The phase offset controls which sample maps to angle 0, driving rotation.
                val path = Path()
                val startOffset = ((phase.floatValue / 360f) * samples.size).toInt().let {
                    ((it % samples.size) + samples.size) % samples.size  // handle negative modulo
                }
                for (i in 0 until SAMPLE_COUNT) {
                    val theta = (i.toDouble() / (SAMPLE_COUNT - 1)) * 2.0 * Math.PI
                    val sampleIdx = (startOffset + i * samples.size / SAMPLE_COUNT) % samples.size
                    val displacement = samples[sampleIdx] * scale * waveAmplitude
                    val r = (ringRadius + displacement).toFloat()
                    val x = (center.x + r * kotlin.math.cos(theta)).toFloat()
                    val y = (center.y + r * kotlin.math.sin(theta)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = ringColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                // Fallback: no samples yet — draw a simple ring
                drawCircle(
                    color = ringColor.copy(alpha = 0.4f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

private const val SPEED_SCALE = 3.35f
private const val EXPO_K = 0.08f
private const val TOLERANCE_CENTS = 5f
private const val SAMPLE_COUNT = 361

// Rotation speed EMA: lower = more smoothing, higher = more responsive.
// 0.06 gives ~17 frames (~280ms at 60fps) to fully settle — removes jitter without lag.
private const val SPEED_SMOOTH_ALPHA = 0.06f

// Below this speed (degrees/second) the ring is considered stopped to avoid micro-drift.
private const val SPEED_DEAD_ZONE = 0.5f

// Waveform blend rate per display frame: 0.4 blends ~93% of the way in 4 frames (~67ms).
// Fast enough to track audio frames (~25ms apart) without visible shape jumps.
private const val WAVEFORM_BLEND_ALPHA = 0.4f
