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
 * Canvas-based strobe ring with waveform animation.
 *
 * Renders a sinusoidal waveform circle that rotates by advancing a phase offset.
 * Speed uses exponential decay so motion slows naturally as pitch nears in-tune.
 * Stops completely when within +/-5 cents. Shows a dim static circle when silent.
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
        val waveAmplitude = strokeWidth * WAVE_AMPLITUDE_RATIO
        val phaseRadians = phase.floatValue.toDouble() * (Math.PI / 180.0)

        if (isSilent) {
            // Dim neutral circle — signals "listening" state
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
        } else {
            // Waveform ring: 361 polar-coordinate points (0..360 inclusive closes the loop)
            val path = Path()
            for (i in 0 until SAMPLE_COUNT) {
                val theta = (i.toDouble() / (SAMPLE_COUNT - 1)) * 2.0 * Math.PI
                val displacement = kotlin.math.sin(theta * WAVE_CYCLES + phaseRadians) * waveAmplitude
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
        }
    }
}

private const val SPEED_SCALE = 3.35f
private const val EXPO_K = 0.08f
private const val TOLERANCE_CENTS = 5f
private const val WAVE_CYCLES = 8.0
private const val WAVE_AMPLITUDE_RATIO = 0.30f
private const val SAMPLE_COUNT = 361
