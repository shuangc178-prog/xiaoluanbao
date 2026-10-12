package com.example.campuslife.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsChartHitTest {
    @Test fun barsUseTheSameScaleAndPreserveProportions() {
        assertEquals(1f, learningBarFraction(60_000L, 60_000L), 0.0001f)
        assertEquals(0.5f, learningBarFraction(30_000L, 60_000L), 0.0001f)
    }
    @Test fun untimedTasksHaveNoBarAndInvalidValuesCannotDrawOutsideTrack() {
        assertEquals(0f, learningBarFraction(0L, 0L), 0f)
        assertEquals(0f, learningBarFraction(-1L, 60_000L), 0f)
        assertEquals(1f, learningBarFraction(90_000L, 60_000L), 0f)
    }
}
