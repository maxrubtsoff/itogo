package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.DayValue
import ru.maxrubtsoff.bankagg.domain.model.DaySpan
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.HistoryRow
import ru.maxrubtsoff.bankagg.domain.model.HistoryRows
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import java.time.LocalDate

/** The days a chart point stands for and what they touch in the list (F-46). */
class HistorySpanTest {
    private val start = LocalDate.of(2026, 9, 1)
    private val today = LocalDate.of(2026, 10, 25)

    private fun days(n: Int) = (0 until n).map { DayValue(start.plusDays(it.toLong()), 1_000L * it) }

    private fun ev(date: LocalDate) = HistoryEvent(date, CapitalPart.ACCOUNTS, "t", null, 100L, kind = HistoryKind.INCOME)

    @Test fun weeklyKeepsLastAndStepsSevenDays() {
        val s = days(30)
        val w = CapitalHistory.weekly(s)
        assertEquals(s.last(), w.last())
        assertEquals(listOf(1L, 7L, 7L, 7L, 7L), w.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a.date, b.date) })
    }

    @Test fun weeklyKeepsFirst() {
        assertEquals(days(30).first(), CapitalHistory.weekly(days(30)).first())
        assertEquals(2, CapitalHistory.weekly(days(8)).size)
        assertEquals(days(2), CapitalHistory.weekly(days(2)))
    }

    @Test fun spanAtFirstPointIsItsDay() {
        val sp = CapitalHistory.spanAt(days(5), 0)
        assertEquals(DaySpan(start, start), sp)
        assertEquals(1, sp.days)
    }

    @Test fun spanAtMiddleCoversGap() {
        val w = CapitalHistory.weekly(days(30))
        val sp = CapitalHistory.spanAt(w, 2)
        assertEquals(7, sp.days)
        assertEquals(w[2].date, sp.to)
        assertTrue(w[1].date !in sp)
        assertTrue(w[1].date.plusDays(1) in sp)
    }

    @Test fun stepAtDifferenceAndNullAtStart() {
        val s = days(3)
        assertNull(CapitalHistory.stepAt(s, 0))
        assertEquals(1_000L, CapitalHistory.stepAt(s, 2))
    }

    @Test fun touchesByAnySource() {
        val inside = ev(LocalDate.of(2026, 10, 10))
        val outside = ev(LocalDate.of(2026, 10, 1))
        val group = HistoryRow.Group("g", inside.date, HistoryKind.INCOME, 2, 200L, listOf(outside), "t", null, ru.maxrubtsoff.bankagg.domain.model.RowKind.OPEN, sources = listOf(outside, inside))
        val span = DaySpan(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 14))
        assertTrue(HistoryRows.touches(group, span))
        assertFalse(HistoryRows.touches(HistoryRow.Single(outside), span))
        assertFalse(HistoryRows.touches(group, null))
        assertEquals(group, HistoryRows.firstTouching(listOf(HistoryRow.Single(outside), group), span))
    }

    @Test fun focusDayNewestInSpan() {
        val events = listOf(ev(LocalDate.of(2026, 10, 9)), ev(LocalDate.of(2026, 10, 12)), ev(LocalDate.of(2026, 10, 20)))
        assertEquals(LocalDate.of(2026, 10, 12), CapitalEvents.focusDay(events, DaySpan(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 14))))
    }

    @Test fun focusDayFallsBackToNearest() {
        val events = listOf(ev(LocalDate.of(2026, 10, 2)), ev(LocalDate.of(2026, 10, 20)))
        val one = DaySpan(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18))
        assertEquals(LocalDate.of(2026, 10, 20), CapitalEvents.focusDay(events, one))
        assertNull(CapitalEvents.focusDay(emptyList(), one))
    }

    @Test fun spanTextSameAndCrossMonth() {
        assertEquals("14 октября", RuDays.span(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14), today))
        assertEquals("8 – 14 октября", RuDays.span(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 14), today))
        assertEquals("28 сентября – 4 октября", RuDays.span(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), today))
    }

    @Test fun spanWordDayWeekDays() {
        assertEquals("за день", RuDays.spanWord(1))
        assertEquals("за неделю", RuDays.spanWord(7))
        assertEquals("за 3 дня", RuDays.spanWord(3))
        assertEquals("за 14 дней", RuDays.spanWord(14))
    }

    @Test fun pointOfEventInWeeklySpan() {
        val w = CapitalHistory.weekly(days(30))
        val i = CapitalHistory.pointOf(w, w[2].date.minusDays(3))
        assertEquals(2, i)
        assertNull(CapitalHistory.pointOf(w, start.minusDays(1)))
        assertNull(CapitalHistory.pointOf(w, w.last().date.plusDays(1)))
    }

    @Test fun averageOfSeries() {
        assertEquals(1_000L, CapitalHistory.average(days(3)))
        assertEquals(0L, CapitalHistory.average(emptyList()))
    }
}
