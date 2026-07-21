package com.thetuner.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp

/**
 * Perspective guitar string lines converging to a vanishing point.
 *
 * Draws 6 lines representing guitar strings from a near plane (spread apart)
 * to a far plane (converging). The detected string is illuminated in its
 * assigned color (or green when in tune), while undetected strings are dim.
 *
 * String lines are clipped to the ring outer edge so they appear to feed into
 * the ring rather than exiting the other side.
 */
@Composable
fun StringsOverlay(
    detectedStringIndex: Int?,
    stringColors: List<Color>,
    isInTune: Boolean,
    inTuneColor: Color,
    ringRadiusDp: Dp,
    modifier: Modifier = Modifier
) {
    val clipCircle = remember { Path() }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Near plane (bottom) and far plane (top) Y coordinates
        val nearY = centerY + size.height * 0.35f
        val farY = centerY - size.height * 0.35f

        // Spread at near and far planes
        val nearSpread = size.width * 0.3f
        val farSpread = size.width * 0.03f

        // Clip to ring outer edge — DrawScope implements Density so toPx() works directly
        val ringOuterRadiusPx = ringRadiusDp.toPx()
        val clipCenter = Offset(size.width / 2f, size.height / 2f)
        clipCircle.reset()
        clipCircle.addOval(Rect(center = clipCenter, radius = ringOuterRadiusPx))

        clipPath(clipCircle, clipOp = ClipOp.Intersect) {
            for (i in 0 until STRING_COUNT) {
                // Normalize position: -1 to +1
                val t = (i - 2.5f) / 2.5f
                val nearX = centerX + t * nearSpread
                val farX = centerX + t * farSpread

                val isDetected = detectedStringIndex == i
                val color = when {
                    isDetected && isInTune -> inTuneColor
                    isDetected -> stringColors.getOrElse(i) { Color.White.copy(alpha = 0.15f) }
                    else -> Color.White.copy(alpha = 0.15f)
                }
                val strokeWidth = if (isDetected) 3f else 1.5f

                drawLine(
                    color = color,
                    start = Offset(nearX, nearY),
                    end = Offset(farX, farY),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

private const val STRING_COUNT = 6
