package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPast
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

/** The saved past follows deposits entered with an earlier date (B-79). */
class CapitalPastTest {

    private val today = LocalDate.of(2026, 7, 1)
    private val all = setOf(CapitalPart.ACCOUNTS, CapitalPart.DEPOSITS, CapitalPart.CURRENCY, CapitalPart.CASH)

    private fun pt(day: LocalDate, acc: Long = 500L, dep: Long = 0L, cur: Long = 70L, cash: Long = 30L) =
        CapitalPoint(day, acc + dep + cur + cash, mapOf(
            CapitalPart.ACCOUNTS to acc, CapitalPart.DEPOSITS to dep, CapitalPart.CURRENCY to cur, CapitalPart.CASH to cash
        ))

    private fun dep(start: LocalDate, end: LocalDate = today.plusDays(100), ups: List<DepositTopUp> = emptyList()) =
        Deposit("d", BankBrand.SBER, null, Money.ofMinor(1_000_000L), 1600, start, end, topUps = ups)

    private fun List<CapitalPoint>.on(day: LocalDate) = firstOrNull { it.date == day }

    private fun daily(from: LocalDate, to: LocalDate): List<CapitalPoint> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { pt(it) }.toList()

    @Test fun deposit_added_today_with_past_start_lands_on_start_date() {
        val start = LocalDate.of(2026, 6, 1)
        val out = CapitalPast.withDeposits(daily(LocalDate.of(2026, 5, 1), today), listOf(dep(start)), all, today)
        assertEquals(1_000_000L, out.on(start)!!.parts[CapitalPart.DEPOSITS])
        assertNull(out.on(start.minusDays(1)))
        assertEquals(500L + 1_000_000L + 70L + 30L, out.on(start)!!.totalMinor)
    }

    @Test fun missing_start_day_is_created_with_carried_parts() {
        val start = LocalDate.of(2026, 6, 1)
        val pts = listOf(pt(LocalDate.of(2026, 5, 28), acc = 111L), pt(LocalDate.of(2026, 6, 5), acc = 222L))
        val made = CapitalPast.withDeposits(pts, listOf(dep(start)), all, today).on(start)!!
        assertEquals(111L, made.parts[CapitalPart.ACCOUNTS])
        assertEquals(70L, made.parts[CapitalPart.CURRENCY])
        assertEquals(30L, made.parts[CapitalPart.CASH])
        assertEquals(1_000_000L, made.parts[CapitalPart.DEPOSITS])
    }

    @Test fun deposit_older_than_history_prepends_day_before_start() {
        val start = LocalDate.of(2026, 6, 1)
        val pts = listOf(pt(LocalDate.of(2026, 6, 10), acc = 321L))
        val out = CapitalPast.withDeposits(pts, listOf(dep(start)), all, today)
        assertEquals(0L, out.on(start.minusDays(1))!!.parts[CapitalPart.DEPOSITS])
        assertEquals(0L, out.on(start.minusDays(1))!!.parts[CapitalPart.ACCOUNTS] ?: 0L)
        assertEquals(1_000_000L, out.on(start)!!.parts[CapitalPart.DEPOSITS])
    }

    @Test fun topup_and_close_days_get_points() {
        val start = LocalDate.of(2026, 6, 1)
        val up = LocalDate.of(2026, 6, 15)
        val end = LocalDate.of(2026, 6, 20)
        val pts = listOf(pt(LocalDate.of(2026, 5, 30)), pt(LocalDate.of(2026, 6, 25)))
        val out = CapitalPast.withDeposits(pts, listOf(dep(start, end, listOf(DepositTopUp("u", up, Money.ofMinor(50_000L))))), all, today)
        assertTrue(out.on(up)!!.parts[CapitalPart.DEPOSITS]!! > 1_000_000L)
        assertTrue(out.on(end)!!.parts[CapitalPart.DEPOSITS]!! > 0L)
        // A deposit is active through its end day; it is gone from the next.
        assertEquals(0L, out.on(end.plusDays(1))!!.parts[CapitalPart.DEPOSITS])
    }

    @Test fun today_is_never_touched() {
        val out = CapitalPast.withDeposits(daily(LocalDate.of(2026, 5, 1), today), listOf(dep(LocalDate.of(2026, 6, 1))), all, today)
        assertNull(out.on(today))
    }

    @Test fun second_run_changes_nothing() {
        val first = daily(LocalDate.of(2026, 5, 1), today)
        val deps = listOf(dep(LocalDate.of(2026, 4, 20)))
        val out = CapitalPast.withDeposits(first, deps, all, today)
        val merged = (first.filter { p -> out.none { it.date == p.date } } + out)
        assertTrue(CapitalPast.withDeposits(merged, deps, all, today).isEmpty())
    }

    @Test fun total_follows_only_when_deposits_included() {
        val start = LocalDate.of(2026, 6, 1)
        val pts = daily(LocalDate.of(2026, 5, 30), today)
        val out = CapitalPast.withDeposits(pts, listOf(dep(start)), all - CapitalPart.DEPOSITS, today)
        assertEquals(1_000_000L, out.on(start)!!.parts[CapitalPart.DEPOSITS])
        assertEquals(600L, out.on(start)!!.totalMinor)
    }

    // B-85: currency deals dated in the past.
    private val usd = listOf(FxRate("USD", LocalDate.of(2026, 5, 1), 90_000_000L))

    private fun buy(day: LocalDate) = FxOperation("f", "USD", FxKind.BUY, day, 100_000L, 9_000_000L, 0L)

    @Test fun backdated_fx_buy_shifts_currency_from_its_date() {
        val day = today.minusDays(7)
        val out = CapitalPast.withFxChange(daily(today.minusDays(14), today), emptyList(), listOf(buy(day)), usd, all, today)
        assertNull(out.on(day.minusDays(1)))
        assertEquals(70L + 9_000_000L, out.on(day)!!.parts[CapitalPart.CURRENCY])
        assertEquals(70L + 9_000_000L, out.on(day.plusDays(1))!!.parts[CapitalPart.CURRENCY])
        assertEquals(600L + 9_000_000L, out.on(day)!!.totalMinor)
    }

    @Test fun removed_fx_deal_is_taken_out_of_past() {
        val day = today.minusDays(7)
        val pts = daily(today.minusDays(14), today).map { if (!it.date.isBefore(day)) pt(it.date, cur = 9_000_070L) else it }
        val out = CapitalPast.withFxChange(pts, listOf(buy(day)), emptyList(), usd, all, today)
        assertEquals(70L, out.on(day)!!.parts[CapitalPart.CURRENCY])
    }

    @Test fun unchanged_fx_list_changes_nothing() {
        val l = listOf(buy(today.minusDays(7)))
        assertTrue(CapitalPast.withFxChange(daily(today.minusDays(14), today), l, l, usd, all, today).isEmpty())
    }

    @Test fun fx_shift_never_touches_today() {
        val out = CapitalPast.withFxChange(daily(today.minusDays(14), today), emptyList(), listOf(buy(today.minusDays(7))), usd, all, today)
        assertNull(out.on(today))
    }

    @Test fun withDeposits_dayBeforeHistory_hasNoAccounts() {
        val first = LocalDate.of(2026, 5, 1)
        val start = first.minusDays(10)
        val made = CapitalPast.withDeposits(daily(first, today), listOf(dep(start)), all, today).on(start)!!
        assertEquals(0L, made.parts[CapitalPart.ACCOUNTS] ?: 0L)
        assertEquals(1_000_000L, made.parts[CapitalPart.DEPOSITS])
    }
}
