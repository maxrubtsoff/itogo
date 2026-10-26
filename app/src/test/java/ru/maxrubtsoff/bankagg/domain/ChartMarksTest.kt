package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.ChartMarks
import ru.maxrubtsoff.bankagg.domain.model.DayValue
import ru.maxrubtsoff.bankagg.domain.model.ForeignAmount
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.MarkShape
import java.time.LocalDate

/** Which actions the chart marks, with which shape and sum (F-49). */
class ChartMarksTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val series = (0 until 20).map { DayValue(start.plusDays(it.toLong()), 1_000_000L) }
    private val avg = 1_000_000L

    private fun ev(day: Int, kind: HistoryKind, part: CapitalPart, amount: Long, foreign: ForeignAmount? = null) =
        HistoryEvent(start.plusDays(day.toLong()), part, "t", null, amount, kind = kind, foreign = foreign)

    private fun of(events: List<HistoryEvent>, page: CapitalPart? = null, avg: Long = this.avg, s: List<DayValue> = series) =
        ChartMarks.of(s, events, page, avg)

    @Test fun onlyActionsAreMarked() {
        val events = listOf(
            ev(1, HistoryKind.INTEREST, CapitalPart.DEPOSITS, 900_000L), ev(2, HistoryKind.REVALUATION, CapitalPart.CURRENCY, 900_000L),
            ev(3, HistoryKind.OWN_TRANSFER, CapitalPart.ACCOUNTS, 900_000L)
        )
        assertTrue(of(events).isEmpty())
    }

    @Test fun accountsThresholdTenPercentOfAverage() {
        assertEquals(1, of(listOf(ev(1, HistoryKind.SPEND, CapitalPart.ACCOUNTS, -110_000L)), CapitalPart.ACCOUNTS).size)
        assertTrue(of(listOf(ev(1, HistoryKind.SPEND, CapitalPart.ACCOUNTS, -90_000L)), CapitalPart.ACCOUNTS).isEmpty())
        assertTrue(of(listOf(ev(1, HistoryKind.SPEND, CapitalPart.ACCOUNTS, -90_000L)), avg = 0L).isEmpty())
        assertTrue(of(listOf(ev(1, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 5_000_000L)), avg = -5L).isEmpty())
    }

    @Test fun currencyOperationsAreNotMarked() {
        assertTrue(of(listOf(ev(1, HistoryKind.SPEND, CapitalPart.CURRENCY, -5_000_000L))).isEmpty())
    }

    @Test fun depositsPageShapes() {
        val m = of(
            listOf(
                ev(1, HistoryKind.DEPOSIT_OPEN, CapitalPart.DEPOSITS, 500_000L), ev(2, HistoryKind.DEPOSIT_TOPUP, CapitalPart.DEPOSITS, 100_000L),
                ev(3, HistoryKind.DEPOSIT_CLOSE, CapitalPart.DEPOSITS, -300_000L)
            ), CapitalPart.DEPOSITS
        )
        assertEquals(listOf(MarkShape.BIG, MarkShape.SMALL, MarkShape.RING), m.map { it.shape })
        assertNull(m[1].label)
        assertNotNull(m[2].label)
    }

    @Test fun totalPageSkipsOwnMoneyMovesToDeposits() {
        val events = listOf(
            ev(1, HistoryKind.DEPOSIT_OPEN, CapitalPart.DEPOSITS, 500_000L), ev(2, HistoryKind.DEPOSIT_TOPUP, CapitalPart.DEPOSITS, 100_000L),
            ev(3, HistoryKind.FX_BUY, CapitalPart.CURRENCY, 100_000L)
        )
        val m = of(events)
        assertEquals(1, m.size)
        assertEquals(MarkShape.SMALL, m[0].shape)
        assertNull(m[0].label)
    }

    @Test fun fxLabelInDealCurrency() {
        val m = of(
            listOf(
                ev(1, HistoryKind.FX_BUY, CapitalPart.CURRENCY, 90_000_00L, ForeignAmount("USD", 100_000L)),
                ev(2, HistoryKind.FX_SELL, CapitalPart.CURRENCY, -45_000_00L, ForeignAmount("EUR", -50_000L))
            ), CapitalPart.CURRENCY
        )
        assertEquals("+" + ru.maxrubtsoff.bankagg.domain.model.Money.ofMinor(100_000L).format("USD"), m[0].label)
        assertEquals("−" + ru.maxrubtsoff.bankagg.domain.model.Money.ofMinor(50_000L).format("EUR"), m[1].label)
    }

    @Test fun atMostThreeLabelsLargestFirst() {
        val events = (1..5).map { ev(it, HistoryKind.INCOME, CapitalPart.ACCOUNTS, it * 1_000_000L) }
        val m = of(events, CapitalPart.ACCOUNTS)
        assertEquals(5, m.size)
        assertEquals(listOf(3, 4, 5), m.filter { it.label != null }.map { it.index })
    }

    @Test fun onePerPointPrefersBig() {
        val m = of(
            listOf(ev(2, HistoryKind.DEPOSIT_TOPUP, CapitalPart.DEPOSITS, 900_000L), ev(2, HistoryKind.DEPOSIT_OPEN, CapitalPart.DEPOSITS, 100_000L)),
            CapitalPart.DEPOSITS
        )
        assertEquals(1, m.size)
        assertEquals(MarkShape.BIG, m[0].shape)
    }

    @Test fun eventInWeeklySpanGoesToItsPoint() {
        val w = CapitalHistory.weekly(series)
        val m = of(listOf(ev(10, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 500_000L)), CapitalPart.ACCOUNTS, s = w)
        assertEquals(CapitalHistory.pointOf(w, start.plusDays(10)), m.single().index)
    }

    @Test fun snapWithinTolerance() = assertEquals(5, ChartMarks.snap(4, listOf(1, 5, 12), 2))
    @Test fun snapOutsideKeepsRaw() = assertEquals(8, ChartMarks.snap(8, listOf(1, 5, 12), 2))
    @Test fun snapTiePrefersEarlier() = assertEquals(4, ChartMarks.snap(5, listOf(4, 6), 2))
}
