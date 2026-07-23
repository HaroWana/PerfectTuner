package com.thetuner.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PenTrackerTest {

    @Test
    fun `silence before any note lifts the pen`() {
        val tracker = PenTracker()
        assertEquals(
            listOf(TraceSample(1L, cents = null, inTune = false, stringIndex = null)),
            tracker.samplesFor(1L, isSilent = true, cents = 0f, inTune = false, stringIndex = null)
        )
    }

    @Test
    fun `active sample passes through`() {
        val tracker = PenTracker()
        assertEquals(
            listOf(TraceSample(1L, cents = -12f, inTune = false, stringIndex = 2)),
            tracker.samplesFor(1L, isSilent = false, cents = -12f, inTune = false, stringIndex = 2)
        )
    }

    @Test
    fun `silence after a note holds the last value`() {
        val tracker = PenTracker()
        tracker.samplesFor(1L, isSilent = false, cents = 7f, inTune = true, stringIndex = 3)
        assertEquals(
            listOf(TraceSample(2L, cents = 7f, inTune = true, stringIndex = 3)),
            tracker.samplesFor(2L, isSilent = true, cents = 0f, inTune = false, stringIndex = null)
        )
    }

    @Test
    fun `resuming the same string after a hold continues the line`() {
        val tracker = PenTracker()
        tracker.samplesFor(1L, isSilent = false, cents = 7f, inTune = false, stringIndex = 3)
        tracker.samplesFor(2L, isSilent = true, cents = 0f, inTune = false, stringIndex = null)
        assertEquals(
            listOf(TraceSample(3L, cents = 5f, inTune = false, stringIndex = 3)),
            tracker.samplesFor(3L, isSilent = false, cents = 5f, inTune = false, stringIndex = 3)
        )
    }

    @Test
    fun `a new string after a hold breaks the line with a pen lift`() {
        val tracker = PenTracker()
        tracker.samplesFor(1L, isSilent = false, cents = 7f, inTune = false, stringIndex = 3)
        tracker.samplesFor(2L, isSilent = true, cents = 0f, inTune = false, stringIndex = null)
        assertEquals(
            listOf(
                TraceSample(3L, cents = null, inTune = false, stringIndex = null),
                TraceSample(3L, cents = -30f, inTune = false, stringIndex = 4)
            ),
            tracker.samplesFor(3L, isSilent = false, cents = -30f, inTune = false, stringIndex = 4)
        )
    }

    @Test
    fun `string switch while actively playing does not break the line`() {
        val tracker = PenTracker()
        tracker.samplesFor(1L, isSilent = false, cents = 7f, inTune = false, stringIndex = 3)
        assertEquals(
            listOf(TraceSample(2L, cents = -30f, inTune = false, stringIndex = 4)),
            tracker.samplesFor(2L, isSilent = false, cents = -30f, inTune = false, stringIndex = 4)
        )
    }

    @Test
    fun `reset clears the held value`() {
        val tracker = PenTracker()
        tracker.samplesFor(1L, isSilent = false, cents = 7f, inTune = false, stringIndex = 3)
        tracker.reset()
        assertEquals(
            listOf(TraceSample(2L, cents = null, inTune = false, stringIndex = null)),
            tracker.samplesFor(2L, isSilent = true, cents = 0f, inTune = false, stringIndex = null)
        )
    }
}
