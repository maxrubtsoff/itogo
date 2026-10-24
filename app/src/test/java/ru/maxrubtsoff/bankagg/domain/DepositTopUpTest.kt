package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

class DepositTopUpTest {

    private val start = LocalDate.of(2026, 1, 1)
    private val end = LocalDate.of(2027, 1, 1)

    private fun dep(method: AccrualMethod, vararg ups: DepositTopUp) = Deposit(
        id = "d", brand = BankBrand.SBER, principal = Money.ofMajor(100_000.0), rateBp = 1000,
        startDate = start, endDate = end, method = method, topUps = ups.toList()
    )

    private val midYear = DepositTopUp("t", LocalDate.of(2026, 7, 2), Money.ofMajor(50_000.0))

    @Test fun topUp_addsItsOwnIncomeFromItsDay() {
        for (m in AccrualMethod.entries) {
            val plain = DepositMath.project(dep(m), end)
            val topped = DepositMath.project(dep(m, midYear), end)
            val extra = topped.totalIncome.minor - plain.totalIncome.minor
            // 50 000 at 10% for ~half a year: about 2 500 ₽ (a bit more with compounding).
            assertTrue("$m: $extra", extra in 240_000L..270_000L)
            assertEquals(plain.payoutAmount.minor + 5_000_000L + extra, topped.payoutAmount.minor)
        }
    }

    @Test fun beforeTheTopUp_itIsNotThere() {
        val may = LocalDate.of(2026, 5, 1)
        assertEquals(
            DepositMath.project(dep(AccrualMethod.LINEAR), may).currentValue,
            DepositMath.project(dep(AccrualMethod.LINEAR, midYear), may).currentValue
        )
        val aug = LocalDate.of(2026, 8, 1)
        assertTrue(
            DepositMath.project(dep(AccrualMethod.LINEAR, midYear), aug).currentValue.minor >
                DepositMath.project(dep(AccrualMethod.LINEAR), aug).currentValue.minor + 5_000_000L - 1
        )
    }

    @Test fun invested_countsTopUpsMadeByTheDay() {
        val d = dep(AccrualMethod.LINEAR, midYear)
        assertEquals(Money.ofMajor(100_000.0), d.investedAt(LocalDate.of(2026, 7, 1)))
        assertEquals(Money.ofMajor(150_000.0), d.investedAt(LocalDate.of(2026, 7, 2)))
        assertEquals(Money.ofMajor(150_000.0), d.investedTotal)
    }

    @Test fun bars_riseFromTheTopUpMonth() {
        val bars = DepositMath.monthBars(dep(AccrualMethod.PAYOUT, midYear), LocalDate.of(2026, 10, 1))
        val june = bars.first { it.month == YearMonth.of(2026, 6) }
        val july = bars.first { it.month == YearMonth.of(2026, 7) }
        assertEquals(Money.ofMajor(100_000.0), june.balance)
        assertEquals(Money.ofMajor(150_000.0), july.balance)
        assertTrue(july.income.minor > june.income.minor)
    }

    @Test fun topUpOutsideTheTerm_isIgnoredInIncome() {
        val late = DepositTopUp("x", LocalDate.of(2027, 2, 1), Money.ofMajor(10_000.0))
        assertEquals(
            DepositMath.project(dep(AccrualMethod.LINEAR), end).totalIncome,
            DepositMath.project(dep(AccrualMethod.LINEAR, late), end).totalIncome
        )
    }
}
