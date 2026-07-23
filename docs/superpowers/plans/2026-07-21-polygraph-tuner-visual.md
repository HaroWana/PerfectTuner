# Polygraph Tuner Visual Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the strobe ring with a vertical polygraph-style pitch trace (pen at top, paper scrolls down, x = cents) above a labeled perspective string overlay.

**Architecture:** A pure `TraceGeometry` module (sample model, eviction, coordinate mapping, segmentation — fully unit-tested) feeds a `PitchTrace` Canvas composable that appends one sample per display frame from `TunerState` and renders colored segments with green in-tune fills. `StringsOverlay` loses its ring clip and gains note labels; `TunerScreen` becomes a Column (trace top ~55%, strings bottom). `StrobeRing` and the waveform plumbing are deleted.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2025.03.01), JUnit 4. Spec: `docs/superpowers/specs/2026-07-21-polygraph-tuner-visual-design.md`.

## Global Constraints

- Gradle needs `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (default JDK 21 is JRE-only).
- Conventional Commits; NO co-author trailers; never commit `.planning/` or `.superpowers/`.
- Trace window: `WINDOW_MS = 5000L`. Cents clamp: `CENTS_RANGE = 50f`. In-tune tolerance is ±5c and lives in the engine — the UI only reads `TunerState.isInTune`.
- Colors: screen background `Color(0xFF121212)`, trace panel `Color(0xFF1A1A1A)`, gridlines `Color(0xFF2C2C2C)`, in-tune `StringColors.inTuneGreen`, string palette `StringColors.palette`, neutral `StringColors.neutralColor`.
- No engine logic changes: `TunerEngine`'s cents/isInTune pipeline and `TunerEngineTest` must remain untouched (Task 7 only removes the unused waveform field/method).
- Frame-loop code must follow the existing review lessons: no per-frame `Path` allocation (reuse + `reset()`), positions computed from timestamps (frame-rate independent), no boxing in hot loops.
- Verify command (used by several tasks): `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

---

### Task 1: TraceGeometry — sample model and coordinate mapping

**Files:**
- Create: `app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt`
- Test: `app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin, no Compose/Android imports — that is what makes it unit-testable).
- Produces (used by Tasks 2–4):
  - `data class TraceSample(val timeMs: Long, val cents: Float?, val inTune: Boolean, val stringIndex: Int?)` — `cents = null` means pen lifted (silence).
  - `TraceGeometry.WINDOW_MS: Long = 5000L`, `TraceGeometry.CENTS_RANGE: Float = 50f`
  - `TraceGeometry.centsToX(cents: Float, width: Float, inset: Float): Float`
  - `TraceGeometry.timeToY(timeMs: Long, nowMs: Long, height: Float): Float`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt`:

```kotlin
package com.thetuner.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TraceGeometryTest {

    // --- centsToX: width 200, inset 20 -> usable half-width is 80 ---

    @Test
    fun `zero cents maps to horizontal center`() {
        assertEquals(100f, TraceGeometry.centsToX(0f, 200f, 20f), 0.001f)
    }

    @Test
    fun `plus 50 cents maps to right edge minus inset`() {
        assertEquals(180f, TraceGeometry.centsToX(50f, 200f, 20f), 0.001f)
    }

    @Test
    fun `minus 50 cents maps to left edge plus inset`() {
        assertEquals(20f, TraceGeometry.centsToX(-50f, 200f, 20f), 0.001f)
    }

    @Test
    fun `cents beyond range clamp to the edge`() {
        // engine can emit up to ±200c for far-off strings
        assertEquals(180f, TraceGeometry.centsToX(200f, 200f, 20f), 0.001f)
        assertEquals(20f, TraceGeometry.centsToX(-120f, 200f, 20f), 0.001f)
    }

    // --- timeToY: pen row at top, window bottom at height ---

    @Test
    fun `current time maps to top`() {
        assertEquals(0f, TraceGeometry.timeToY(10_000L, 10_000L, 400f), 0.001f)
    }

    @Test
    fun `window-old sample maps to bottom`() {
        assertEquals(400f, TraceGeometry.timeToY(5_000L, 10_000L, 400f), 0.001f)
    }

    @Test
    fun `half-window-old sample maps to middle`() {
        assertEquals(200f, TraceGeometry.timeToY(7_500L, 10_000L, 400f), 0.001f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.thetuner.app.ui.TraceGeometryTest" 2>&1 | tail -20`
Expected: compilation FAILURE — `Unresolved reference: TraceGeometry`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt`:

```kotlin
package com.thetuner.app.ui

data class TraceSample(
    val timeMs: Long,
    val cents: Float?, // null = pen lifted (silence)
    val inTune: Boolean,
    val stringIndex: Int?
)

object TraceGeometry {
    const val WINDOW_MS = 5000L
    const val CENTS_RANGE = 50f

    fun centsToX(cents: Float, width: Float, inset: Float): Float {
        val clamped = cents.coerceIn(-CENTS_RANGE, CENTS_RANGE)
        return width / 2f + (clamped / CENTS_RANGE) * (width / 2f - inset)
    }

    fun timeToY(timeMs: Long, nowMs: Long, height: Float): Float {
        return (nowMs - timeMs).toFloat() / WINDOW_MS * height
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.thetuner.app.ui.TraceGeometryTest" 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt
git commit -m "feat(trace): add TraceGeometry sample model and coordinate mapping"
```

---

### Task 2: TraceGeometry — segmentation

**Files:**
- Modify: `app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt`
- Test: `app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt` (append)

**Interfaces:**
- Consumes: `TraceSample` from Task 1.
- Produces (used by Task 4):
  - `data class SegmentRange(val start: Int, val endExclusive: Int, val inTune: Boolean, val stringIndex: Int?)` — indices into the sample list. When a run directly follows another run (no silence gap), `start` overlaps the previous run's last index so drawn polylines connect without a break.
  - `TraceGeometry.segment(samples: List<TraceSample>): List<SegmentRange>` — contiguous runs of drawable (`cents != null`) samples sharing `(inTune, stringIndex)`. Runs shorter than 2 points (after overlap extension) are dropped — a single point can't form a line.

- [ ] **Step 1: Write the failing tests**

Append to `TraceGeometryTest.kt` (inside the class):

```kotlin
    private fun sample(
        timeMs: Long,
        cents: Float? = 0f,
        inTune: Boolean = false,
        stringIndex: Int? = 0
    ) = TraceSample(timeMs, cents, inTune, stringIndex)

    @Test
    fun `empty samples produce no segments`() {
        assertEquals(emptyList<SegmentRange>(), TraceGeometry.segment(emptyList()))
    }

    @Test
    fun `all-silent samples produce no segments`() {
        val samples = List(5) { sample(it.toLong(), cents = null) }
        assertEquals(emptyList<SegmentRange>(), TraceGeometry.segment(samples))
    }

    @Test
    fun `uniform run produces one segment`() {
        val samples = List(5) { sample(it.toLong()) }
        assertEquals(
            listOf(SegmentRange(0, 5, inTune = false, stringIndex = 0)),
            TraceGeometry.segment(samples)
        )
    }

    @Test
    fun `silence gap splits segments without overlap`() {
        val samples = List(3) { sample(it.toLong()) } +
            sample(3L, cents = null) +
            List(3) { sample(4L + it) }
        assertEquals(
            listOf(
                SegmentRange(0, 3, inTune = false, stringIndex = 0),
                SegmentRange(4, 7, inTune = false, stringIndex = 0)
            ),
            TraceGeometry.segment(samples)
        )
    }

    @Test
    fun `inTune flip splits segments with one-sample overlap for continuity`() {
        val samples = List(3) { sample(it.toLong(), inTune = false) } +
            List(3) { sample(3L + it, inTune = true) }
        assertEquals(
            listOf(
                SegmentRange(0, 3, inTune = false, stringIndex = 0),
                SegmentRange(2, 6, inTune = true, stringIndex = 0)
            ),
            TraceGeometry.segment(samples)
        )
    }

    @Test
    fun `string switch splits segments with one-sample overlap`() {
        val samples = List(2) { sample(it.toLong(), stringIndex = null) } +
            List(4) { sample(2L + it, stringIndex = 0) }
        assertEquals(
            listOf(
                SegmentRange(0, 2, inTune = false, stringIndex = null),
                SegmentRange(1, 6, inTune = false, stringIndex = 0)
            ),
            TraceGeometry.segment(samples)
        )
    }

    @Test
    fun `single sample surrounded by silence is dropped`() {
        val samples = listOf(
            sample(0L, cents = null),
            sample(1L),
            sample(2L, cents = null)
        )
        assertEquals(emptyList<SegmentRange>(), TraceGeometry.segment(samples))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.thetuner.app.ui.TraceGeometryTest" 2>&1 | tail -20`
Expected: compilation FAILURE — `Unresolved reference: SegmentRange` / `segment`.

- [ ] **Step 3: Write minimal implementation**

Add to `TraceGeometry.kt` — `SegmentRange` at file level (below `TraceSample`), `segment` inside the `object`:

```kotlin
data class SegmentRange(
    val start: Int, // overlaps previous run's last index when runs are contiguous
    val endExclusive: Int,
    val inTune: Boolean,
    val stringIndex: Int?
)
```

```kotlin
    fun segment(samples: List<TraceSample>): List<SegmentRange> {
        val ranges = mutableListOf<SegmentRange>()
        var i = 0
        while (i < samples.size) {
            if (samples[i].cents == null) {
                i++
                continue
            }
            var j = i + 1
            while (j < samples.size &&
                samples[j].cents != null &&
                samples[j].inTune == samples[i].inTune &&
                samples[j].stringIndex == samples[i].stringIndex
            ) {
                j++
            }
            // Borrow the previous drawable point so contiguous runs connect visually
            val start = if (i > 0 && samples[i - 1].cents != null) i - 1 else i
            if (j - start >= 2) {
                ranges.add(SegmentRange(start, j, samples[i].inTune, samples[i].stringIndex))
            }
            i = j
        }
        return ranges
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.thetuner.app.ui.TraceGeometryTest" 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt
git commit -m "feat(trace): add trace segmentation with gap and state splits"
```

---

### Task 3: TraceGeometry — eviction

**Files:**
- Modify: `app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt`
- Test: `app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt` (append)

**Interfaces:**
- Consumes: `TraceSample`, `WINDOW_MS` from Task 1.
- Produces (used by Task 4): `TraceGeometry.evictExpired(samples: ArrayDeque<TraceSample>, nowMs: Long)` — removes samples older than the window but always keeps one sample past the cutoff so the trace line exits the bottom edge smoothly instead of ending mid-panel.

- [ ] **Step 1: Write the failing tests**

Append to `TraceGeometryTest.kt`:

```kotlin
    @Test
    fun `evictExpired keeps exactly one sample older than the window`() {
        // window 5000; now 10000 -> cutoff 5000
        val samples = ArrayDeque(
            listOf(sample(3_000L), sample(4_000L), sample(6_000L))
        )
        TraceGeometry.evictExpired(samples, 10_000L)
        assertEquals(listOf(sample(4_000L), sample(6_000L)), samples.toList())
    }

    @Test
    fun `evictExpired keeps all samples inside the window`() {
        val samples = ArrayDeque(listOf(sample(6_000L), sample(7_000L)))
        TraceGeometry.evictExpired(samples, 10_000L)
        assertEquals(2, samples.size)
    }

    @Test
    fun `evictExpired is a no-op on empty and single-sample buffers`() {
        val empty = ArrayDeque<TraceSample>()
        TraceGeometry.evictExpired(empty, 10_000L)
        assertEquals(0, empty.size)

        val single = ArrayDeque(listOf(sample(1_000L)))
        TraceGeometry.evictExpired(single, 10_000L)
        assertEquals(1, single.size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.thetuner.app.ui.TraceGeometryTest" 2>&1 | tail -20`
Expected: compilation FAILURE — `Unresolved reference: evictExpired`.

- [ ] **Step 3: Write minimal implementation**

Add inside the `TraceGeometry` object:

```kotlin
    fun evictExpired(samples: ArrayDeque<TraceSample>, nowMs: Long) {
        val cutoff = nowMs - WINDOW_MS
        // Keep one sample past the cutoff so the trace exits the bottom edge smoothly
        while (samples.size >= 2 && samples[1].timeMs < cutoff) {
            samples.removeFirst()
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.thetuner.app.ui.TraceGeometryTest" 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thetuner/app/ui/TraceGeometry.kt app/src/test/java/com/thetuner/app/ui/TraceGeometryTest.kt
git commit -m "feat(trace): add trace sample eviction with bottom-edge overlap"
```

---

### Task 4: PitchTrace composable

**Files:**
- Create: `app/src/main/java/com/thetuner/app/ui/PitchTrace.kt`

**Interfaces:**
- Consumes: `TraceSample`, `SegmentRange`, `TraceGeometry.{centsToX, timeToY, segment, evictExpired}` from Tasks 1–3.
- Produces (used by Task 6):
  ```kotlin
  @Composable
  fun PitchTrace(
      centsOffset: Float,
      isInTune: Boolean,
      isSilent: Boolean,
      detectedStringIndex: Int?,
      stringColors: List<Color>,
      inTuneColor: Color,
      neutralColor: Color,
      modifier: Modifier = Modifier
  )
  ```
- No unit test: this is Canvas/frame-loop code with no test harness in the project (same status as StrobeRing had). Correctness is delegated to TraceGeometry's tests; this task verifies by compilation and Task 6 verifies on device.

- [ ] **Step 1: Write the composable**

Create `app/src/main/java/com/thetuner/app/ui/PitchTrace.kt`:

```kotlin
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
 * in tune render green with a soft fill toward the center line; silence
 * lifts the pen, leaving gaps while the paper keeps scrolling.
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

    LaunchedEffect(Unit) {
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                samples.addLast(
                    TraceSample(
                        timeMs = frameTimeMs,
                        cents = if (currentSilent) null else currentCents,
                        inTune = currentInTune,
                        stringIndex = currentString
                    )
                )
                TraceGeometry.evictExpired(samples, frameTimeMs)
                frameTick.longValue = frameTimeMs
            }
        }
    }

    Canvas(modifier = modifier) {
        val nowMs = frameTick.longValue
        val inset = 24.dp.toPx()
        val centerX = size.width / 2f

        drawRoundRect(
            color = Color(0xFF1A1A1A),
            cornerRadius = CornerRadius(16.dp.toPx())
        )

        // Gridlines at ±25c
        for (cents in intArrayOf(-25, 25)) {
            val x = TraceGeometry.centsToX(cents.toFloat(), size.width, inset)
            drawLine(
                color = Color(0xFF2C2C2C),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
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
        val labelStyle = TextStyle(color = Color(0xFF666666), fontSize = 10.sp)
        for ((cents, text) in AXIS_LABELS) {
            val layout = textMeasurer.measure(text, labelStyle)
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
                fillPath.reset()
                var first = true
                for (i in range.start until range.endExclusive) {
                    val s = samples[i]
                    val cents = s.cents ?: continue
                    val x = TraceGeometry.centsToX(cents, size.width, inset)
                    val y = TraceGeometry.timeToY(s.timeMs, nowMs, size.height)
                    if (first) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, y)
                        first = false
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                if (range.inTune) {
                    // Soft fill between the in-tune trace and the center line
                    val yLast = TraceGeometry.timeToY(samples[range.endExclusive - 1].timeMs, nowMs, size.height)
                    val yFirst = TraceGeometry.timeToY(samples[range.start].timeMs, nowMs, size.height)
                    fillPath.lineTo(centerX, yLast)
                    fillPath.lineTo(centerX, yFirst)
                    fillPath.close()
                    drawPath(fillPath, color = inTuneColor.copy(alpha = 0.2f))
                }

                drawPath(linePath, color = color, style = Stroke(width = 3.dp.toPx()))
            }

            // Pen head on the newest drawable sample
            val latest = samples.lastOrNull { it.cents != null }
            if (latest != null && nowMs - latest.timeMs < PEN_FADE_MS) {
                drawCircle(
                    color = segmentColor(latest.inTune, latest.stringIndex, stringColors, inTuneColor, neutralColor),
                    radius = 5.dp.toPx(),
                    center = Offset(
                        TraceGeometry.centsToX(latest.cents ?: 0f, size.width, inset),
                        TraceGeometry.timeToY(latest.timeMs, nowMs, size.height)
                    )
                )
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

private val AXIS_LABELS = listOf(-25f to "-25", 0f to "0", 25f to "+25")
```

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`. (An "unused" warning for PitchTrace is fine — it's wired up in Task 6.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thetuner/app/ui/PitchTrace.kt
git commit -m "feat(trace): add PitchTrace polygraph composable"
```

---

### Task 5: StringsOverlay — labels, no ring clip, converge upward

**Files:**
- Modify: `app/src/main/java/com/thetuner/app/ui/StringsOverlay.kt` (full rewrite below)

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Task 6 — note `ringRadiusDp` is GONE and `stringLabels` is NEW):
  ```kotlin
  @Composable
  fun StringsOverlay(
      detectedStringIndex: Int?,
      stringColors: List<Color>,
      isInTune: Boolean,
      inTuneColor: Color,
      stringLabels: List<String>,
      modifier: Modifier = Modifier
  )
  ```
- NOTE: `TunerScreen` still calls the old signature after this task, so `:app:compileDebugKotlin` will FAIL until Task 6 lands. Verify this task with the unit test suite only (it doesn't compile the app module's callers? — it does; so instead verify by `./gradlew :app:compileDebugKotlin` AFTER Task 6, and here only check the file for syntax by eye). To keep every task independently green, Task 5 and Task 6 are committed together in Task 6's commit if compilation is the gate you want — but the default here is: make this edit, expect the compile break, and proceed immediately to Task 6 before committing.

**Execution note:** Tasks 5 and 6 form one commit-atomic pair (the signature change and its only caller). Complete both, then run the verify command and commit them together in Task 6 Step 4.

- [ ] **Step 1: Rewrite the file**

Replace the entire contents of `app/src/main/java/com/thetuner/app/ui/StringsOverlay.kt` with:

```kotlin
package com.thetuner.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val nearY = size.height * 0.78f
        val farY = 0f
        val nearSpread = size.width * 0.38f
        val farSpread = size.width * 0.06f
        val dimColor = Color.White.copy(alpha = 0.15f)

        for (i in 0 until STRING_COUNT) {
            // Normalize position: -1 to +1
            val t = (i - 2.5f) / 2.5f
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

            val label = stringLabels.getOrElse(i) { "" }
            val style = TextStyle(
                color = if (isDetected) color else Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp,
                fontWeight = if (isDetected) FontWeight.Bold else FontWeight.Normal
            )
            val layout = textMeasurer.measure(label, style)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(nearX - layout.size.width / 2f, nearY + 8.dp.toPx())
            )
        }
    }
}

private const val STRING_COUNT = 6
```

- [ ] **Step 2: Proceed to Task 6** (compile is expected to fail until TunerScreen is updated; no separate commit here).

---

### Task 6: TunerScreen layout swap + delete StrobeRing

**Files:**
- Modify: `app/src/main/java/com/thetuner/app/ui/TunerScreen.kt` (full rewrite below)
- Delete: `app/src/main/java/com/thetuner/app/ui/StrobeRing.kt`

**Interfaces:**
- Consumes: `PitchTrace` (Task 4), new `StringsOverlay` signature (Task 5), `TuningLibrary.findById`, `STANDARD_TUNING` (`com.thetuner.app.tuner`), `StringColors.{palette, inTuneGreen, neutralColor}`.
- Produces: same public `TunerScreen` signature as today (callers unchanged).
- `state.waveformSamples` is no longer read anywhere after this task — Task 7 removes it from the model.

- [ ] **Step 1: Rewrite TunerScreen**

Replace the entire contents of `app/src/main/java/com/thetuner/app/ui/TunerScreen.kt` with:

```kotlin
package com.thetuner.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thetuner.app.tuner.STANDARD_TUNING
import com.thetuner.app.tuner.TuningLibrary
import com.thetuner.app.ui.theme.StringColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    hasPurchased: Boolean,
    onNavigateToSettings: () -> Unit,
    onLockedTuningTap: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTuningId by viewModel.activeTuningId.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.startListening()
        onPauseOrDispose {
            viewModel.stopListening()
        }
    }

    val centsOffset = state.centsOffset
    val detectedStringIndex = state.detectedStringIndex
    val isInTune = state.isInTune
    val isSilent = state.isSilent

    val targetDetectedStringColor = when {
        detectedStringIndex != null && isInTune -> StringColors.inTuneGreen
        detectedStringIndex != null -> StringColors.palette[detectedStringIndex]
        else -> StringColors.neutralColor
    }
    val detectedStringColor by animateColorAsState(
        targetValue = targetDetectedStringColor,
        animationSpec = tween(durationMillis = 200),
        label = "detectedStringColor"
    )

    val stringColors = remember(detectedStringIndex, detectedStringColor) {
        StringColors.palette.mapIndexed { index, color ->
            if (index == detectedStringIndex) detectedStringColor else color
        }
    }

    val activeTuning = TuningLibrary.findById(activeTuningId)
    // Chromatic mode has no strings; the overlay lights the nearest Standard
    // string (existing engine behavior), so label with Standard's note names.
    val labelTuning = if (activeTuning.strings.isEmpty()) STANDARD_TUNING else activeTuning
    val stringLabels = remember(labelTuning.id) { labelTuning.strings.map { it.noteName } }

    val fabLabel = when (activeTuning.id) {
        "chromatic" -> "Chr"
        "standard" -> "Std"
        "eb_standard" -> "Eb"
        "d_standard" -> "D Std"
        "drop_d" -> "Drop D"
        "drop_c" -> "Drop C"
        else -> activeTuning.name.take(6)
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    // skipPartiallyExpanded = true: prevents sheet dismissal conflict when scrolling LazyColumn
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PitchTrace(
                centsOffset = centsOffset,
                isInTune = isInTune,
                isSilent = isSilent,
                detectedStringIndex = detectedStringIndex,
                stringColors = StringColors.palette,
                inTuneColor = StringColors.inTuneGreen,
                neutralColor = StringColors.neutralColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            StringsOverlay(
                detectedStringIndex = detectedStringIndex,
                stringColors = stringColors,
                isInTune = isInTune,
                inTuneColor = StringColors.inTuneGreen,
                stringLabels = stringLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            )
        }

        AssistChip(
            onClick = { showBottomSheet = true },
            label = {
                Text(
                    text = fabLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            },
            shape = RoundedCornerShape(50),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color(0xFF2C2C2C)
            ),
            border = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        )

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E)
            ) {
                TuningPickerSheet(
                    activeTuningId = activeTuningId,
                    hasPurchased = hasPurchased,
                    onTuningSelected = { id ->
                        viewModel.selectTuning(id)
                        showBottomSheet = false
                    },
                    onLockedTuningTap = { tuningId ->
                        showBottomSheet = false
                        onLockedTuningTap(tuningId)
                    },
                    onNavigateToSettings = {
                        showBottomSheet = false
                        onNavigateToSettings()
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 2: Delete StrobeRing**

```bash
git rm app/src/main/java/com/thetuner/app/ui/StrobeRing.kt
```

- [ ] **Step 3: Verify no stale references**

Run: `grep -rn "StrobeRing\|RING_RADIUS_DP\|ringRadiusDp" app/src/main/java/ app/src/test/java/ || echo CLEAN`
Expected: `CLEAN`.

- [ ] **Step 4: Build and test (covers Tasks 5+6)**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL` — all suites green including `TraceGeometryTest` and `TunerEngineTest`.

- [ ] **Step 5: Commit (Tasks 5 and 6 together — signature change + its only caller)**

```bash
git add app/src/main/java/com/thetuner/app/ui/StringsOverlay.kt app/src/main/java/com/thetuner/app/ui/TunerScreen.kt
git commit -m "feat(ui): replace strobe ring with polygraph trace layout

Vertical pitch trace (pen at top, 5 s window) in the top half over the
labeled perspective string overlay in the bottom half. Removes the
strobe ring, the center note/cents readout, and the vignette."
```

---

### Task 7: Remove waveform plumbing

**Files:**
- Modify: `app/src/main/java/com/thetuner/app/tuner/TunerState.kt`
- Modify: `app/src/main/java/com/thetuner/app/tuner/TunerEngine.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TunerState` without `waveformSamples`; no caller reads it after Task 6.
- Behavior note: with the `FloatArray` gone, `TunerState` regains value equality, so `MutableStateFlow` will conflate identical consecutive states. That is safe: the trace samples the *latest* state once per display frame; nothing counts emissions. `TunerEngineTest` awaits distinct marker frequencies, so it is unaffected.

- [ ] **Step 1: Remove the field from TunerState**

In `TunerState.kt`, delete the `waveformSamples` property AND its comment block, leaving:

```kotlin
package com.thetuner.app.tuner

data class TunerState(
    val noteName: String? = null,
    val octave: Int? = null,
    val frequencyHz: Float = 0f,
    val isListening: Boolean = false,
    val isSilent: Boolean = true,
    val centsOffset: Float = 0f,
    val detectedStringIndex: Int? = null,
    val isInTune: Boolean = false,
    val activeTuningId: String = "standard"
)
```

- [ ] **Step 2: Remove producer code in TunerEngine**

In `TunerEngine.kt`:
1. In `processFrame`, delete the line `waveformSamples = downsampleFrame(frame)` from the `TunerState(...)` constructor call (and the trailing comma on the line above it).
2. Delete the entire `downsampleFrame` function:

```kotlin
    private fun downsampleFrame(frame: FloatArray, targetSize: Int = 256): FloatArray {
        val result = FloatArray(targetSize)
        val step = frame.size.toFloat() / targetSize
        for (i in 0 until targetSize) {
            result[i] = frame[(i * step).toInt()]
        }
        return result
    }
```

- [ ] **Step 3: Verify no stale references**

Run: `grep -rn "waveformSamples\|downsampleFrame" app/src/ || echo CLEAN`
Expected: `CLEAN`.

- [ ] **Step 4: Build and run the full suite**

Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL` — all suites green, `TunerEngineTest` untouched and passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thetuner/app/tuner/TunerState.kt app/src/main/java/com/thetuner/app/tuner/TunerEngine.kt
git commit -m "refactor(tuner): remove waveform plumbing obsoleted by trace UI"
```

---

## Final Verification

- [ ] Full build + tests: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest 2>&1 | tail -5` → `BUILD SUCCESSFUL`
- [ ] On-device check: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:installDebug`, then verify: trace scrolls smoothly; pluck a string → pen appears at its cents offset and converges to the dashed line; in-tune stretch turns green with fill and the string label goes green; stop playing → pen lifts, gaps scroll away; switch tuning via chip → labels update (Drop D shows D A D G B E); chromatic mode shows Standard labels and nearest-semitone behavior.
