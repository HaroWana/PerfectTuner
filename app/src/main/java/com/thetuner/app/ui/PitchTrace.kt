package com.thetuner.app.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

/**
 * Polygraph-style pitch trace: pen at the top, paper scrolls downward.
 * X axis is cents (left = flat, right = sharp, clamped to ±CENTS_RANGE);
 * the dashed center line is the in-tune target. Segments captured while
 * in tune render green with a soft fill toward the center line. During
 * silence the pen holds its last value in a straight line; it lifts only
 * before the first note and when a different string starts.
 */
@Composable
fun PitchTrace(
    centsOffset: Float,
    isInTune: Boolean,
    isSilent: Boolean,
    detectedStringIndex: Int?,
    stringColors: List<Color>,
    inTuneColor: Color,
    neutralColor: Color,
    showToleranceMarkers: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentCents by rememberUpdatedState(centsOffset)
    val currentInTune by rememberUpdatedState(isInTune)
    val currentSilent by rememberUpdatedState(isSilent)
    val currentString by rememberUpdatedState(detectedStringIndex)

    // Main-thread only: the frame loop writes, the draw pass reads. frameTick is
    // the sole snapshot state — bumping it once per display frame drives redraw.
    val samples = remember { ArrayDeque<TraceSample>() }
    val frameTick = remember { mutableLongStateOf(0L) }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }
    val textMeasurer = rememberTextMeasurer()
    val axisLayouts = remember(textMeasurer) { AXIS_LABELS.map { (cents, text) -> cents to textMeasurer.measure(text, AXIS_LABEL_STYLE) } }

    LaunchedEffect(Unit) {
        val penTracker = PenTracker()
        var prevFrameTimeMs = 0L
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (prevFrameTimeMs != 0L && frameTimeMs - prevFrameTimeMs > FRAME_GAP_RESET_MS) {
                    samples.clear()
                    penTracker.reset()
                }
                for (sample in penTracker.samplesFor(
                    timeMs = frameTimeMs,
                    isSilent = currentSilent,
                    cents = currentCents,
                    inTune = currentInTune,
                    stringIndex = currentString
                )) {
                    samples.addLast(sample)
                }
                TraceGeometry.evictExpired(samples, frameTimeMs)
                frameTick.longValue = frameTimeMs
                prevFrameTimeMs = frameTimeMs
            }
        }
    }

    Canvas(modifier = modifier) {
        val nowMs = frameTick.longValue
        val inset = 24.dp.toPx()
        val centerX = size.width / 2f
        // Pen rides a quarter down the panel so the freshest strokes stay visible
        val penY = size.height * PEN_ROW_FRACTION

        drawRoundRect(
            color = Color(0xFF1A1A1A),
            cornerRadius = CornerRadius(16.dp.toPx())
        )

        // Gridlines at ±25c
        for (cents in GRID_CENTS) {
            val x = TraceGeometry.centsToX(cents, size.width, inset)
            drawLine(
                color = Color(0xFF2C2C2C),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }

        // ±5c in-tune zone boundaries (Settings > "Show tolerance markers")
        if (showToleranceMarkers) {
            for (cents in TOLERANCE_MARKER_CENTS) {
                val x = TraceGeometry.centsToX(cents, size.width, inset)
                drawLine(
                    color = inTuneColor.copy(alpha = 0.3f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
        }

        // Dashed in-tune center line
        drawLine(
            color = inTuneColor.copy(alpha = 0.6f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
        )

        // Axis labels along the bottom edge
        for ((cents, layout) in axisLayouts) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    TraceGeometry.centsToX(cents, size.width, inset) - layout.size.width / 2f,
                    size.height - layout.size.height - 4.dp.toPx()
                )
            )
        }

        clipRect {
            val ranges = TraceGeometry.segment(samples)
            for (range in ranges) {
                val color = segmentColor(range.inTune, range.stringIndex, stringColors, inTuneColor, neutralColor)

                linePath.reset()
                if (range.inTune) fillPath.reset()
                // Midpoint quadratic smoothing: each segment curves through the
                // midpoint using the previous point as control — no hard joints
                var first = true
                var prevX = 0f
                var prevY = 0f
                for (i in range.start until range.endExclusive) {
                    val s = samples[i]
                    val cents = s.cents ?: continue
                    val x = TraceGeometry.centsToX(cents, size.width, inset)
                    val y = TraceGeometry.timeToY(s.timeMs, nowMs, size.height, penY)
                    if (first) {
                        linePath.moveTo(x, y)
                        if (range.inTune) fillPath.moveTo(x, y)
                        first = false
                    } else {
                        val midX = (prevX + x) / 2f
                        val midY = (prevY + y) / 2f
                        linePath.quadraticTo(prevX, prevY, midX, midY)
                        if (range.inTune) fillPath.quadraticTo(prevX, prevY, midX, midY)
                    }
                    prevX = x
                    prevY = y
                }
                if (!first) {
                    linePath.lineTo(prevX, prevY)
                    if (range.inTune) fillPath.lineTo(prevX, prevY)
                }

                if (range.inTune) {
                    // Soft fill between the in-tune trace and the center line
                    val yLast = TraceGeometry.timeToY(samples[range.endExclusive - 1].timeMs, nowMs, size.height, penY)
                    val yFirst = TraceGeometry.timeToY(samples[range.start].timeMs, nowMs, size.height, penY)
                    fillPath.lineTo(centerX, yLast)
                    fillPath.lineTo(centerX, yFirst)
                    fillPath.close()
                    drawPath(fillPath, color = inTuneColor.copy(alpha = 0.2f))
                }

                drawPath(linePath, color = color, style = Stroke(width = 3.dp.toPx()))
            }

            // Pen head on the newest drawable sample; fades out after the pen lifts
            val latest = samples.lastOrNull { it.cents != null }
            if (latest != null) {
                val age = nowMs - latest.timeMs
                if (age < PEN_FADE_MS) {
                    val fade = 1f - age.toFloat() / PEN_FADE_MS
                    val color = segmentColor(latest.inTune, latest.stringIndex, stringColors, inTuneColor, neutralColor)
                    drawCircle(
                        color = color.copy(alpha = color.alpha * fade),
                        radius = 5.dp.toPx(),
                        center = Offset(
                            TraceGeometry.centsToX(latest.cents ?: 0f, size.width, inset),
                            TraceGeometry.timeToY(latest.timeMs, nowMs, size.height, penY)
                        )
                    )
                }
            }
        }
    }
}

private fun segmentColor(
    inTune: Boolean,
    stringIndex: Int?,
    stringColors: List<Color>,
    inTuneColor: Color,
    neutralColor: Color
): Color = when {
    inTune -> inTuneColor
    stringIndex != null -> stringColors.getOrElse(stringIndex) { neutralColor }
    else -> neutralColor
}

// Pen dot lingers briefly after the pen lifts, then disappears
private const val PEN_FADE_MS = 300L

// A gap this long means frames stopped (app backgrounded) — restart the trace
private const val FRAME_GAP_RESET_MS = 500L

private const val PEN_ROW_FRACTION = 0.25f

private val GRID_CENTS = floatArrayOf(-25f, 25f)
private val TOLERANCE_MARKER_CENTS = floatArrayOf(-5f, 5f)
private val AXIS_LABEL_STYLE = TextStyle(color = Color(0xFF666666), fontSize = 10.sp)
private val AXIS_LABELS = listOf(-25f to "-25", 0f to "0", 25f to "+25")
