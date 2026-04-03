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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Canvas-based strobe ring with phase accumulator rotation.
 *
 * The ring consists of 8 alternating segments that rotate proportionally
 * to the cent offset. Rotation stops completely when within +/-5 cents.
 *
 * Direction convention:
 * - Flat pitch (negative cents) -> clockwise rotation
 * - Sharp pitch (positive cents) -> counterclockwise rotation
 */
@Composable
fun StrobeRing(
    centsOffset: Float,
    ringColor: Color,
    showToleranceMarkers: Boolean = false,
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
                    phase.floatValue += -currentCentsOffset * SPEED_SCALE * deltaSeconds
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val ringDiameter = size.minDimension * 0.85f
        val strokeWidth = ringDiameter * 0.175f
        val ringRadius = (ringDiameter - strokeWidth) / 2f
        val topLeft = Offset(
            (size.width - ringDiameter + strokeWidth) / 2f,
            (size.height - ringDiameter + strokeWidth) / 2f
        )
        val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

        rotate(degrees = phase.floatValue, pivot = center) {
            val segmentAngle = 360f / SEGMENT_COUNT
            for (i in 0 until SEGMENT_COUNT) {
                val startAngle = i * segmentAngle
                val color = if (i % 2 == 0) ringColor else ringColor.copy(alpha = 0.15f)
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = segmentAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }
        }

        // Tolerance markers (optional)
        if (showToleranceMarkers) {
            val markerLength = strokeWidth * 0.4f
            val outerRadius = ringRadius + strokeWidth / 2f
            val innerRadius = outerRadius - markerLength
            val markerColor = Color.White.copy(alpha = 0.5f)
            val markerStroke = 2f

            // Draw tick marks at approximately +/-5 cent positions near 12 o'clock
            for (angle in listOf(-3f, 3f)) {
                val rad = Math.toRadians(angle.toDouble() - 90.0)
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                drawLine(
                    color = markerColor,
                    start = Offset(center.x + innerRadius * cos, center.y + innerRadius * sin),
                    end = Offset(center.x + outerRadius * cos, center.y + outerRadius * sin),
                    strokeWidth = markerStroke
                )
            }
        }
    }
}

private const val SPEED_SCALE = 3.6f
private const val TOLERANCE_CENTS = 5f
private const val SEGMENT_COUNT = 8
