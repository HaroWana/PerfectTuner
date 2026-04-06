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
    // rememberUpdatedState so the LaunchedEffect always reads the latest centsOffset
    // without restarting the coroutine on every recomposition
    val currentCentsOffset by rememberUpdatedState(centsOffset)

    // Frame-driven phase accumulator
    LaunchedEffect(Unit) {
        var prevFrameTime = 0L
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                val deltaSeconds = if (prevFrameTime == 0L) 0f else (frameTimeMs - prevFrameTime) / 1000f
                prevFrameTime = frameTimeMs

                // Only rotate when outside tolerance
                if (abs(currentCentsOffset) > TOLERANCE_CENTS) {
                    val absCents = abs(currentCentsOffset)
                    val speed = SPEED_SCALE * (exp(EXPO_K * absCents) - 1f)
                    phase.floatValue = (phase.floatValue - sign(currentCentsOffset) * speed * deltaSeconds) % 360f
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
            val samples = waveformSamples
            if (samples != null && samples.isNotEmpty()) {
                // Render real audio waveform around the ring.
                // The phase offset controls which sample maps to angle 0, driving rotation.
                val path = Path()
                val startOffset = ((phase.floatValue / 360f) * samples.size).toInt().let {
                    ((it % samples.size) + samples.size) % samples.size  // handle negative modulo
                }
                for (i in 0 until SAMPLE_COUNT) {
                    val theta = (i.toDouble() / (SAMPLE_COUNT - 1)) * 2.0 * Math.PI
                    val sampleIdx = (startOffset + i * samples.size / SAMPLE_COUNT) % samples.size
                    val displacement = samples[sampleIdx] * waveAmplitude
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
