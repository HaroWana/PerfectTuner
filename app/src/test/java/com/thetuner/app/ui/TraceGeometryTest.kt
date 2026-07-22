package com.thetuner.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayDeque

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
}
