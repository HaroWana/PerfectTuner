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
