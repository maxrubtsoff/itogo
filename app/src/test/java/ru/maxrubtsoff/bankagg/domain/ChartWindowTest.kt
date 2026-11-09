package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.ChartWindow
import ru.maxrubtsoff.bankagg.domain.model.DaySpan
import ru.maxrubtsoff.bankagg.domain.model.DayValue
import java.time.LocalDate

class ChartWindowTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun series(n: Int) = (0 until n).map { DayValue(start.plusDays(it.toLong()), it * 100L) }
    private fun span(n: Int) = DaySpan(start, start.plusDays(n - 1L))
    private val bounds = span(365)

    @Test fun zoomInKeepsFocusDay() {
        val w = ChartWindow.zoom(bounds, bounds, 2.0, 0.5)
        assertTrue(w.days in 182..183)
        val mid = start.plusDays(182)
        assertTrue(mid in w)
        // The middle day stays in the middle.
        val centre = w.from.plusDays((w.days - 1) / 2L)
        assertTrue(Math.abs(java.time.temporal.ChronoUnit.DAYS.between(centre, mid)) <= 1)
    }

    @Test fun zoomClampsToMinDays() {
        val w = ChartWindow.zoom(span(20), bounds, 100.0, 0.5)
        assertEquals(ChartWindow.MIN_DAYS, w.days)
    }

    @Test fun zoomOutClampsToBounds() {
        val w = ChartWindow.zoom(span(100), bounds, 0.01, 0.3)
        assertEquals(bounds, w)
    }

    @Test fun panClampsAtEdges() {
        val w = DaySpan(start.plusDays(10), start.plusDays(39))
        val left = ChartWindow.pan(w, bounds, -5.0)
        assertEquals(bounds.from, left.from); assertEquals(30, left.days)
        val right = ChartWindow.pan(w, bounds, 50.0)
        assertEquals(bounds.to, right.to); assertEquals(30, right.days)
    }

    @Test fun resizeKeepsMinDaysAndBounds() {
        val w = DaySpan(start.plusDays(100), start.plusDays(160))
        val tiny = ChartWindow.resize(w, bounds, startEdge = true, fraction = 1.0)
        assertEquals(ChartWindow.MIN_DAYS, tiny.days); assertEquals(w.to, tiny.to)
        val wide = ChartWindow.resize(w, bounds, startEdge = false, fraction = 1.0)
        assertEquals(bounds.to, wide.to); assertEquals(w.from, wide.from)
    }

    @Test fun centerAtMovesWindow() {
        val w = ChartWindow.centerAt(span(30), bounds, 0.5)
        assertEquals(30, w.days)
        assertTrue(start.plusDays(182) in w)
        assertEquals(bounds.from, ChartWindow.centerAt(span(30), bounds, 0.0).from)
    }

    @Test fun shortPeriodNeverBelowItsOwnLength() {
        val b = span(4)
        assertEquals(b, ChartWindow.zoom(b, b, 10.0, 0.5))
    }

    @Test fun drawnNeverExceedsMaxPoints() {
        val full = series(2000)
        assertTrue(ChartWindow.drawn(ChartWindow.visible(full, ChartWindow.bounds(full))).size <= CapitalHistory.MAX_POINTS)
        assertEquals(60, ChartWindow.drawn(ChartWindow.visible(full, DaySpan(start.plusDays(500), start.plusDays(559)))).size)
    }

    @Test fun drawnKeepsFirstAndLast() {
        val full = series(2000)
        val w = DaySpan(start.plusDays(100), start.plusDays(900))
        val d = ChartWindow.drawn(ChartWindow.visible(full, w))
        assertEquals(w.from, d.first().date); assertEquals(w.to, d.last().date)
    }

    @Test fun fractionsAreByDaysNotByThinnedPoints() {
        // A narrow window of a long period: by days it is a sliver at its place, whatever the thinned points are.
        val b = span(1001)
        val f = ChartWindow.fractions(DaySpan(start.plusDays(500), start.plusDays(530)), b)!!
        assertEquals(0.5f, f.start, 0.0001f)
        assertEquals(0.53f, f.endInclusive, 0.0001f)
        assertNull(ChartWindow.fractions(null, b))
    }

    @Test fun zoomOutAtBoundThenInNarrowsAtOnce() {
        // The window is the whole period: pinching out further keeps nothing for the way back.
        var step = ChartWindow.zoomStep(bounds, bounds, 1.0, 0.5, 0.5)
        assertEquals(bounds, step.window)
        assertEquals(1.0, step.rest, 0.0)
        step = ChartWindow.zoomStep(step.window, bounds, step.rest, 1.5, 0.5)
        assertTrue(step.window.days < bounds.days)
    }

    @Test fun zoomInAtMinThenOutWidensAtOnce() {
        val min = DaySpan(start.plusDays(10), start.plusDays(10 + ChartWindow.MIN_DAYS - 1L))
        var step = ChartWindow.zoomStep(min, bounds, 1.0, 3.0, 0.5)
        assertEquals(1.0, step.rest, 0.0)
        step = ChartWindow.zoomStep(step.window, bounds, step.rest, 0.5, 0.5)
        assertTrue(step.window.days > ChartWindow.MIN_DAYS)
    }

    @Test fun smallStepsAddUp() {
        val w = span(100)
        val a = ChartWindow.zoomStep(w, bounds, 1.0, 1.001, 0.5)
        assertEquals(w, a.window)
        assertTrue(a.rest > 1.0)
    }

    @Test fun visibleNullWindowIsWhole() {
        val full = series(50)
        assertEquals(full, ChartWindow.visible(full, null))
        assertNull(ChartWindow.bounds(emptyList()))
    }
}
