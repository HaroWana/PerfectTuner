package com.thetuner.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Perspective guitar string lines converging toward a vanishing point at the
 * top of the overlay (visually "feeding into" the trace panel above), with
 * note labels along the near plane at the bottom. The detected string is
 * illuminated in its assigned color (or green when in tune); others are dim.
 */
@Composable
fun StringsOverlay(
    detectedStringIndex: Int?,
    stringColors: List<Color>,
    isInTune: Boolean,
    inTuneColor: Color,
    stringLabels: List<String>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    // Measured in composition, not in the draw pass: this composable redraws at
    // audio-state rate and text layout is expensive. Color is applied at draw
    // time so re-measurement only happens on label or detected-string changes.
    val labelLayouts = remember(textMeasurer, stringLabels, detectedStringIndex) {
        stringLabels.mapIndexed { index, label ->
            textMeasurer.measure(
                label,
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (index == detectedStringIndex) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val nearY = size.height * 0.78f
        val farY = 0f
        val nearSpread = size.width * 0.38f
        val farSpread = size.width * 0.06f
        val dimColor = Color.White.copy(alpha = 0.15f)
        val halfSpan = (STRING_COUNT - 1) / 2f

        for (i in 0 until STRING_COUNT) {
            // Normalize position: -1 to +1
            val t = (i - halfSpan) / halfSpan
            val nearX = centerX + t * nearSpread
            val farX = centerX + t * farSpread

            val isDetected = detectedStringIndex == i
            val color = when {
                isDetected && isInTune -> inTuneColor
                isDetected -> stringColors.getOrElse(i) { dimColor }
                else -> dimColor
            }

            drawLine(
                color = color,
                start = Offset(nearX, nearY),
                end = Offset(farX, farY),
                strokeWidth = if (isDetected) 3f else 1.5f
            )

            val layout = labelLayouts.getOrNull(i) ?: continue
            drawText(
                textLayoutResult = layout,
                color = if (isDetected) color else Color.White.copy(alpha = 0.35f),
                topLeft = Offset(nearX - layout.size.width / 2f, nearY + 8.dp.toPx())
            )
        }
    }
}

private const val STRING_COUNT = 6
