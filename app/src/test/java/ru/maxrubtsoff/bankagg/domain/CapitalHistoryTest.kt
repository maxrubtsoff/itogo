package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CapitalHistoryTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun point(day: LocalDate, total: Long, accounts: Long = total) = CapitalPoint(
        day, total,
        mapOf(CapitalPart.ACCOUNTS to accounts, CapitalPart.DEPOSITS to 0L, CapitalPart.CURRENCY to 0L, CapitalPart.CASH to total - accounts)
    )

    @Test fun empty_hasNoSeriesAndNoChange() {
        assertTrue(CapitalHistory.series(emptyList(), HistoryPeriod.MONTH, today).isEmpty())
        assertNull(CapitalHistory.change(emptyList()))
        assertNull(CapitalHistory.glance(emptyList(), today))
    }

    @Test fun onePoint_isNotDrawable() {
        val s = CapitalHistory.series(listOf(point(today, 100)), HistoryPeriod.MONTH, today)
        assertEquals(1, s.size)
        assertFalse(CapitalHistory.drawable(s))
        assertNull(CapitalHistory.glance(listOf(point(today, 100)), today))
    }

    @Test fun week_startsSevenDaysBack_andGapsKeepTheLastValue() {
        val pts = listOf(point(today.minusDays(10), 1000), point(today.minusDays(3), 1500), point(today, 1200))
        val s = CapitalHistory.series(pts, HistoryPeriod.WEEK, today)
        assertEquals(8, s.size)
        assertEquals(today.minusDays(7), s.first().date)
        assertEquals(1000L, s.first().minor)            // carried from before the window
        assertEquals(1500L, s[5].minor)                 // after the third-day point
        assertEquals(1500L, s[6].minor)                 // gap: no fall to zero
        assertEquals(1200L, s.last().minor)
    }

    @Test fun fall_isNegativeDeltaWithPercent() {
        val pts = listOf(point(today.minusDays(30), 200_000), point(today, 150_000))
        val c = CapitalHistory.change(CapitalHistory.series(pts, HistoryPeriod.MONTH, today))!!
        assertEquals(-50_000L, c.deltaMinor)
        assertEquals(-2500L, c.percentBp)               // −25,00 %
    }

    @Test fun startingFromNothing_hasNoPercent() {
        val pts = listOf(point(today.minusDays(2), 0), point(today, 5000))
        val c = CapitalHistory.change(CapitalHistory.series(pts, HistoryPeriod.WEEK, today))!!
        assertEquals(5000L, c.deltaMinor)
        assertNull(c.percentBp)
    }

    @Test fun all_startsAtTheFirstPoint() {
        val pts = listOf(point(today.minusDays(400), 10), point(today, 20))
        val s = CapitalHistory.series(pts, HistoryPeriod.ALL, today)
        assertEquals(today.minusDays(400), s.first().date)
        assertEquals(401, s.size)
    }

    @Test fun thin_keepsFirstAndLast() {
        val pts = listOf(point(today.minusDays(400), 10), point(today, 20))
        val s = CapitalHistory.thin(CapitalHistory.series(pts, HistoryPeriod.ALL, today), 50)
        assertEquals(50, s.size)
        assertEquals(today.minusDays(400), s.first().date)
        assertEquals(today, s.last().date)
    }

    @Test fun partSeries_followsThatPart() {
        val pts = listOf(point(today.minusDays(1), 1000, accounts = 700), point(today, 1300, accounts = 700))
        val total = CapitalHistory.change(CapitalHistory.series(pts, HistoryPeriod.WEEK, today))!!
        val acc = CapitalHistory.change(CapitalHistory.series(pts, HistoryPeriod.WEEK, today, CapitalPart.ACCOUNTS))!!
        assertEquals(300L, total.deltaMinor)
        assertEquals(0L, acc.deltaMinor)
    }

    @Test fun monthDelta_isEndOfMonthAgainstEndOfPrevious() {
        val pts = listOf(point(LocalDate.of(2026, 9, 30), 1000), point(LocalDate.of(2026, 10, 15), 1200), point(LocalDate.of(2026, 10, 31), 1500))
        assertEquals(500L, CapitalHistory.monthDelta(pts, YearMonth.of(2026, 10)))
    }

    @Test fun events_listOperationsAndInterestOncePerDay() {
        val brand = BankBrand.SBER
        val acc = Account("a", brand, "Сбер", Money.ofMinor(5000), "RUB", 0L)
        val at = today.minusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val tx = Transaction("t", brand, "a", Money.ofMinor(5000), "RUB", TransactionKind.INCOME, null, "Работа", at)
        val events = CapitalEvents.build(
            listOf(tx), listOf(acc), emptyList(), emptyList(), CashBalance(Money.ofMinor(100), 0L),
            emptyList(), today, ZoneOffset.UTC
        )
        assertEquals(1, events.size)
        assertEquals("Пополнение", events[0].title)
        assertEquals("Работа", events[0].subtitle)
        assertEquals(5000L, events[0].amountMinor)
    }

    @Test fun reconstruction_walksAccountBackByOperations() {
        val brand = BankBrand.SBER
        val acc = Account("a", brand, "Сбер", Money.ofMinor(100_000), "RUB", 0L)
        val at = today.minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val tx = Transaction("t", brand, "a", Money.ofMinor(30_000), "RUB", TransactionKind.INCOME, null, null, at)
        val pts = CapitalReconstruction.build(
            listOf(acc), listOf(tx), emptyList(), emptyList(), emptyList(), null,
            CapitalPart.entries.toSet(), today, ZoneOffset.UTC
        )
        assertEquals(4, pts.size)
        assertEquals(0L, pts[0].totalMinor)             // before the account was known
        assertEquals(100_000L, pts[1].totalMinor)       // the day it came
        assertEquals(100_000L, pts.last().totalMinor)
    }

    @Test fun sectionsStartWithTotal() {
        val s = CapitalHistory.sections(listOf(CapitalPart.ACCOUNTS, CapitalPart.DEPOSITS))
        assertEquals(listOf(null, CapitalPart.ACCOUNTS, CapitalPart.DEPOSITS), s)
    }

    @Test fun pageOfUnknownPartIsTotal() {
        val s = CapitalHistory.sections(listOf(CapitalPart.ACCOUNTS))
        assertEquals(0, CapitalHistory.pageOf(CapitalPart.CASH, s))
    }

    @Test fun pageOfKnownPart() {
        val s = CapitalHistory.sections(listOf(CapitalPart.ACCOUNTS, CapitalPart.DEPOSITS))
        assertEquals(2, CapitalHistory.pageOf(CapitalPart.DEPOSITS, s))
        assertEquals(0, CapitalHistory.pageOf(null, s))
    }

    @Test fun partOfPageRoundTrip() {
        val s = CapitalHistory.sections(listOf(CapitalPart.ACCOUNTS, CapitalPart.CASH))
        s.forEachIndexed { i, p -> assertEquals(p, CapitalHistory.partOf(i, s)) }
        assertNull(CapitalHistory.partOf(9, s))
    }

    @Test fun singleKeptStillHasTotalAndOne() {
        assertEquals(2, CapitalHistory.sections(listOf(CapitalPart.CASH)).size)
    }
}
