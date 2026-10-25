package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.ChartScale

class ChartScaleTest {

    @Test fun flatSeriesGetsMinSpanCentered() {
        // average 1 000 000, 2 % = 20 000
        assertEquals(990_000L to 1_010_000L, ChartScale.range(listOf(1_000_000L, 1_000_000L, 1_000_000L)))
    }

    @Test fun smallWiggleExpandsToTwoPercent() {
        val (lo, hi) = ChartScale.range(listOf(1_000_000L, 1_001_000L))
        assertEquals(20_010L, hi - lo)
        assertTrue(lo <= 1_000_000L && hi >= 1_001_000L)
    }

    @Test fun largeSpanUntouched() {
        assertEquals(900_000L to 1_100_000L, ChartScale.range(listOf(900_000L, 1_000_000L, 1_100_000L)))
    }

    @Test fun emptySeriesSafe() {
        assertEquals(0L to 1L, ChartScale.range(emptyList()))
    }

    @Test fun singlePointSafe() {
        val (lo, hi) = ChartScale.range(listOf(500_000L))
        assertTrue(hi > lo && lo <= 500_000L && hi >= 500_000L)
    }

    @Test fun negativeAverageUsesAbs() {
        val (lo, hi) = ChartScale.range(listOf(-1_000_000L, -1_000_000L))
        assertEquals(20_000L, hi - lo)
    }

    @Test fun spanNeverZero() {
        val (lo, hi) = ChartScale.range(listOf(0L, 0L))
        assertTrue(hi > lo)
    }
}
