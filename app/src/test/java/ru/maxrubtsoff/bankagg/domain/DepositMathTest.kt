package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.Money
import org.junit.Test
import java.time.LocalDate

class DepositMathTest {

    private fun deposit(
        principalMajor: Double,
        rateBp: Int,
        start: LocalDate,
        end: LocalDate,
        exitMajor: Double? = null,
        method: AccrualMethod = AccrualMethod.LINEAR
    ) = Deposit(
        id = "d", brand = BankBrand.TBANK, title = null,
        principal = Money.ofMajor(principalMajor), rateBp = rateBp,
        startDate = start, endDate = end,
        exitAmount = exitMajor?.let(Money::ofMajor), method = method
    )

    @Test
    fun linear_with_exit_amount_distributes_income_evenly() {
        val d = deposit(500_000.0, 1900, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 1), exitMajor = 547_500.0)
        val p = DepositMath.project(d, LocalDate.of(2026, 10, 20))

        assertEquals(183, p.termDays)
        assertEquals(141, p.elapsedDays)
        assertEquals(4_750_000L, p.totalIncome.minor)          // 47 500 ₽
        assertEquals(54_750_000L, p.payoutAmount.minor)        // 547 500 ₽
        // accrued = round(4 750 000 * 141 / 183) = 3 659 836 коп.
        assertEquals(50_000_000L + 3_659_836L, p.currentValue.minor)
        assertEquals(Math.round(4_750_000.0 / 183), p.dailyAccrual.minor)
    }

    @Test
    fun computed_linear_income_is_simple_interest() {
        val d = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)) // 365 days, 12%
        val p = DepositMath.project(d, LocalDate.of(2026, 1, 1))
        assertEquals(1_200_000L, p.totalIncome.minor) // 12 000 ₽
    }

    @Test
    fun capitalization_compounds_monthly() {
        val d = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), method = AccrualMethod.CAPITALIZATION)
        val p = DepositMath.project(d, LocalDate.of(2026, 1, 1))
        // 100 000 * ((1 + 0.12/12)^12 - 1) = 12 682,50 ₽
        assertEquals(1_268_250L, p.totalIncome.minor)
        assertTrue("capitalization должна давать больше дохода, чем линейная", p.totalIncome.minor > 1_200_000L)
    }

    @Test
    fun payout_keeps_balance_at_principal() {
        val d = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), method = AccrualMethod.PAYOUT)
        val p = DepositMath.project(d, LocalDate.of(2026, 7, 2)) // ~half term
        assertEquals(10_000_000L, p.currentValue.minor)          // тело вклада не растёт
        assertTrue(p.accruedIncome.minor > 0L)                   // проценты всё равно капают
    }

    @Test
    fun summary_sums_across_deposits() {
        val a = deposit(500_000.0, 1900, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 1), exitMajor = 547_500.0)
        val b = deposit(300_000.0, 1800, LocalDate.of(2026, 8, 15), LocalDate.of(2027, 2, 15), exitMajor = 326_400.0)
        val s = DepositMath.summary(listOf(a, b), LocalDate.of(2026, 10, 20))
        assertEquals(2, s.count)
        assertEquals(80_000_000L, s.principal.minor)              // 800 000 ₽
        assertEquals(87_390_000L, s.payout.minor)                // 873 900 ₽
        assertEquals(s.payout.minor - s.principal.minor, s.income.minor)
    }

    @Test
    fun currentValueByBank_groups_and_sums_per_bank() {
        val asOf = LocalDate.of(2026, 10, 20)
        fun dep(brand: BankBrand, id: String, principal: Double, exit: Double) = Deposit(
            id = id, brand = brand, title = null,
            principal = Money.ofMajor(principal), rateBp = 1900,
            startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2027, 1, 1),
            exitAmount = Money.ofMajor(exit), method = AccrualMethod.LINEAR
        )
        val tA = dep(BankBrand.TBANK, "t1", 500_000.0, 560_000.0)
        val tB = dep(BankBrand.TBANK, "t2", 100_000.0, 112_000.0)
        val sber = dep(BankBrand.SBER, "s1", 300_000.0, 333_000.0)

        val byBank = DepositMath.currentValueByBank(listOf(tA, tB, sber), asOf)

        assertEquals(setOf(BankBrand.TBANK, BankBrand.SBER), byBank.keys)
        // T-Bank total = sum of the two deposits' current value on the same date.
        val tExpected = DepositMath.project(tA, asOf).currentValue.minor +
            DepositMath.project(tB, asOf).currentValue.minor
        assertEquals(tExpected, byBank[BankBrand.TBANK]!!.minor)
        assertEquals(DepositMath.project(sber, asOf).currentValue.minor, byBank[BankBrand.SBER]!!.minor)
    }

    @Test
    fun currentValueByBank_emptyWhenNoDeposits() {
        assertTrue(DepositMath.currentValueByBank(emptyList(), LocalDate.of(2026, 10, 20)).isEmpty())
    }

    // ---- monthly breakdown ----

    @Test
    fun months_add_up_to_total_income() {
        for (method in AccrualMethod.entries) {
            val d = deposit(500_000.0, 1900, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 7, 15), method = method)
            val p = DepositMath.project(d, LocalDate.of(2026, 3, 1))
            assertEquals("метод $method", p.totalIncome.minor, p.monthly.sumOf { it.income.minor })
        }
        val withExit = deposit(300_000.0, 1800, LocalDate.of(2026, 8, 15), LocalDate.of(2027, 2, 15), exitMajor = 326_400.0)
        val p = DepositMath.project(withExit, LocalDate.of(2026, 10, 20))
        assertEquals(p.totalIncome.minor, p.monthly.sumOf { it.income.minor })
    }

    @Test
    fun last_day_of_month_is_still_current() {
        val d = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))
        val p = DepositMath.project(d, LocalDate.of(2026, 3, 31))
        val march = p.monthly.first { it.month.monthValue == 3 && it.month.year == 2026 }
        assertEquals(DepositMath.MonthState.CURRENT, march.state)
        val feb = p.monthly.first { it.month.monthValue == 2 }
        assertEquals(DepositMath.MonthState.PAST, feb.state)
    }

    @Test
    fun month_window_keeps_current_month_visible() {
        val d = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1))
        val asOf = LocalDate.of(2026, 3, 10)
        val p = DepositMath.project(d, asOf)
        val window = DepositMath.monthWindow(p.monthly, asOf)
        assertEquals(12, window.size)
        assertTrue(window.any { it.state == DepositMath.MonthState.CURRENT })
        assertEquals(2026, window.first().month.year)
        assertEquals(1, window.first().month.monthValue)
    }

    // ---- active / completed / today ----

    @Test
    fun deposit_is_active_on_its_last_day_and_finished_the_day_after() {
        val d = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 21))
        assertTrue(DepositMath.isActive(d, LocalDate.of(2026, 10, 21)))
        assertTrue(!DepositMath.isActive(d, LocalDate.of(2026, 10, 22)))
    }

    @Test
    fun summary_counts_only_active_deposits() {
        val today = LocalDate.of(2026, 10, 22)
        val done = deposit(100_000.0, 1200, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 21), exitMajor = 109_000.0)
        val live = deposit(200_000.0, 1200, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1))
        val s = DepositMath.summary(listOf(done, live), today)
        assertEquals(1, s.count)
        assertEquals(20_000_000L, s.principal.minor)
        val c = DepositMath.completed(listOf(done, live), today)
        assertEquals(1, c.count)
        assertEquals(900_000L, c.income.minor)
        assertEquals(setOf(BankBrand.TBANK), DepositMath.currentValueByBank(listOf(done, live), today).keys)
        assertEquals(DepositMath.project(live, today).currentValue, DepositMath.currentValueByBank(listOf(done, live), today)[BankBrand.TBANK])
    }

    @Test
    fun upcoming_deposit_is_valued_at_principal_without_accrual() {
        val today = LocalDate.of(2026, 10, 21)
        val d = deposit(150_000.0, 1500, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 5, 1))
        assertTrue(DepositMath.isActive(d, today))
        assertTrue(DepositMath.isUpcoming(d, today))
        val p = DepositMath.project(d, today)
        assertEquals(15_000_000L, p.currentValue.minor)
        assertEquals(0L, p.accruedIncome.minor)
        assertEquals(0L, p.todayAccrual.minor)
        assertEquals(15_000_000L, DepositMath.summary(listOf(d), today).currentValue.minor)
    }

    @Test
    fun today_accrual_is_the_real_daily_step_for_capitalization() {
        val d = deposit(1_000_000.0, 2000, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), method = AccrualMethod.CAPITALIZATION)
        val early = DepositMath.project(d, LocalDate.of(2026, 1, 10)).todayAccrual.minor
        val late = DepositMath.project(d, LocalDate.of(2027, 12, 10)).todayAccrual.minor
        assertTrue("к концу срока начисление за день больше: $early vs $late", late > early)
        val p = DepositMath.project(d, LocalDate.of(2026, 6, 1))
        val next = DepositMath.project(d, LocalDate.of(2026, 6, 2))
        assertEquals(next.accruedIncome.minor - p.accruedIncome.minor, p.todayAccrual.minor)
    }
}
