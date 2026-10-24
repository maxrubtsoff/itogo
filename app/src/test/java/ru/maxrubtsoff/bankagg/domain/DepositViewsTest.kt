package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositGroups
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import java.time.LocalDate
import java.time.YearMonth

/** The deposits header (B-3), bank groups (F-14) and the month chart (F-15, B-7). */
class DepositViewsTest {

    private val nb = Money.NBSP
    private val today = LocalDate.of(2026, 10, 22)

    private fun d(
        id: String, brand: BankBrand, principal: Double, rateBp: Int,
        start: LocalDate, end: LocalDate, method: AccrualMethod = AccrualMethod.LINEAR
    ) = Deposit(id = id, brand = brand, principal = Money.ofMajor(principal), rateBp = rateBp,
        startDate = start, endDate = end, method = method)

    // ---- compact money ----

    @Test fun compact_millionsWithTwoDecimals() {
        assertEquals("2,80${nb}млн", MoneyCompact.format(Money.ofMajor(2_800_000.0)))
        assertEquals("3,18${nb}млн", MoneyCompact.format(Money.ofMajor(3_179_193.51)))
        assertEquals("1,00${nb}млн", MoneyCompact.format(Money.ofMajor(999_999.60)))
        assertEquals("1,20${nb}млрд", MoneyCompact.format(Money.ofMajor(1_204_000_000.0)))
    }

    @Test fun compact_belowMillionWholeRubles() {
        assertEquals("248${nb}764", MoneyCompact.format(Money.ofMajor(248_763.90)))
        assertEquals("+248${nb}764", MoneyCompact.format(Money.ofMajor(248_763.90), signed = true))
        assertEquals("0", MoneyCompact.format(Money.ZERO, signed = true))
        assertEquals("-1${nb}500", MoneyCompact.format(Money.ofMajor(-1_500.0)))
    }

    @Test fun summary_earnedShare() {
        val dep = d("t", BankBrand.TBANK, 1_000_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))
        val s = DepositMath.summary(listOf(dep), LocalDate.of(2026, 7, 2))
        assertEquals(0.5, s.earnedShare, 0.01)
    }

    // ---- bank groups ----

    @Test fun groups_sumPerBank_largestFirst() {
        val a1 = d("a1", BankBrand.ALFA, 300_000.0, 1500, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 3, 1))
        val a2 = d("a2", BankBrand.ALFA, 200_000.0, 1500, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1))
        val t = d("t", BankBrand.TBANK, 400_000.0, 1500, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1))
        val proj = { x: Deposit -> DepositMath.project(x, today) }
        val groups = DepositGroups.byBank(listOf(t, a1, a2), { it }, proj)
        assertEquals(listOf(BankBrand.ALFA, BankBrand.TBANK), groups.map { it.brand })
        val alfa = groups.first()
        assertEquals(listOf("a2", "a1"), alfa.items.map { it.id })   // ends soonest first
        assertEquals(proj(a1).currentValue.minor + proj(a2).currentValue.minor, alfa.value.minor)
        assertEquals(proj(a1).accruedIncome.minor + proj(a2).accruedIncome.minor, alfa.accrued.minor)

        val rev = DepositGroups.byBank(listOf(t, a1, a2), { it }, proj, reversed = true)
        assertEquals(listOf(BankBrand.TBANK, BankBrand.ALFA), rev.map { it.brand })
    }

    @Test fun groups_singleDepositStillAGroup() {
        val t = d("t", BankBrand.TBANK, 400_000.0, 1500, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1))
        val groups = DepositGroups.byBank(listOf(t), { it }, { DepositMath.project(it, today) })
        assertEquals(1, groups.size)
        assertEquals(1, groups.first().items.size)
    }

    // ---- month chart ----

    @Test fun bars_growForPayoutToo() {
        // B-7: interest paid out — the balance stays flat, the bars still grow.
        val dep = d("p", BankBrand.ALFA, 1_000_000.0, 1145, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 10, 31),
            AccrualMethod.PAYOUT)
        val bars = DepositMath.monthBars(dep, today)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), bars.map { it.month })
        assertTrue(bars.zipWithNext().all { (a, b) -> b.height.minor > a.height.minor })
        assertTrue(bars.all { it.balance == dep.principal })
        val total = DepositMath.project(dep, today).totalIncome
        assertEquals(total, bars.last().earned)
        assertEquals(total.minor, bars.sumOf { it.income.minor })
    }

    @Test fun bars_capitalizationBalanceGrows() {
        val dep = d("c", BankBrand.TBANK, 1_000_000.0, 1600, LocalDate.of(2025, 10, 30), LocalDate.of(2026, 11, 30),
            AccrualMethod.CAPITALIZATION)
        val bars = DepositMath.monthBars(dep, today)
        assertEquals(14, bars.size)
        assertTrue(bars.all { it.balance == it.height })
        assertEquals(1, bars.count { it.state == DepositMath.MonthState.CURRENT })
    }

    @Test fun barSizes_fromZeroWithVisibleCap() {
        // 1 mln at 11.45% paid out monthly: income is ~1% of the bar.
        val dep = d("p", BankBrand.ALFA, 1_000_000.0, 1145, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 10, 31),
            AccrualMethod.PAYOUT)
        val bars = DepositMath.monthBars(dep, today)
        val sizes = DepositMath.barSizes(bars, area = 170f, minCap = 12f, extraCap = 16f)
        // From zero: the tallest bar fills the area, the others are almost as tall.
        assertEquals(170f, sizes.maxOf { it.total }, 0.01f)
        assertTrue(sizes.all { it.total > 160f })
        // The best month gets the full cap, the short first month stays near the minimum.
        val best = bars.indices.maxBy { bars[it].income.minor }
        assertEquals(28f, sizes[best].cap, 0.01f)
        assertTrue(sizes[0].cap >= 12f && sizes[0].cap < 16f)
    }

    @Test fun barSizes_noIncomeNoCap() {
        val bars = listOf(
            DepositMath.MonthBar(YearMonth.of(2026, 1), DepositMath.MonthState.FUTURE, Money.ZERO, Money.ZERO, Money.ofMinor(100), Money.ofMinor(100))
        )
        assertEquals(DepositMath.BarSize(140f, 0f), DepositMath.barSizes(bars, area = 140f).single())
    }

    @Test fun bars_longDepositKeepsEveryMonth() {
        val dep = d("l", BankBrand.SBER, 500_000.0, 1000, LocalDate.of(2020, 1, 15), LocalDate.of(2028, 5, 15))
        assertEquals(101, DepositMath.monthBars(dep, today).size)
    }

    @Test fun nextAccrual_isThisMonthsIncomeAtMonthEnd() {
        val dep = d("c", BankBrand.TBANK, 1_000_000.0, 1600, LocalDate.of(2025, 10, 30), LocalDate.of(2026, 11, 30),
            AccrualMethod.CAPITALIZATION)
        val next = DepositMath.nextAccrual(dep, today)!!
        assertEquals(LocalDate.of(2026, 10, 31), next.date)
        val oct = DepositMath.monthBars(dep, today).first { it.month == YearMonth.of(2026, 10) }
        assertEquals(oct.income, next.amount)
    }

    @Test fun nextAccrual_lastMonthEndsWithDeposit_noneWhenFinished() {
        val dep = d("p", BankBrand.ALFA, 1_000_000.0, 1145, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 10, 28))
        assertEquals(LocalDate.of(2026, 10, 28), DepositMath.nextAccrual(dep, today)!!.date)
        assertNull(DepositMath.nextAccrual(dep, LocalDate.of(2026, 11, 2)))
    }
}
