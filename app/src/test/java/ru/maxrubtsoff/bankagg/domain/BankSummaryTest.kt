package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSummary
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

class BankSummaryTest {

    private val today = LocalDate.of(2026, 10, 22)
    private val sber = BankBrand.SBER
    private val other = BankBrand("tbank", "Т-Банк", 0xFFFFDD2D)

    private fun acc(brand: BankBrand, rub: Long, currency: String = "RUB") =
        Account("a$rub$currency", brand, "Счёт", Money.ofMinor(rub * 100L), currency, 0)

    private fun dep(brand: BankBrand, rub: Long, end: LocalDate, vararg ups: DepositTopUp) =
        Deposit("d$rub", brand, principal = Money.ofMinor(rub * 100L), rateBp = 1600, startDate = LocalDate.of(2026, 1, 1), endDate = end, topUps = ups.toList())

    @Test fun countsOnlyThisBank_roubleAccounts_openDeposits_andMonthCashback() {
        val s = BankSummary.of(
            "sber",
            accounts = listOf(acc(sber, 100), acc(sber, 50, "USD"), acc(other, 999)),
            deposits = listOf(
                dep(sber, 1000, LocalDate.of(2027, 1, 1), DepositTopUp("u", LocalDate.of(2026, 5, 1), Money.ofMinor(200 * 100L))),
                dep(sber, 7, LocalDate.of(2026, 1, 31)),
                dep(other, 5000, LocalDate.of(2027, 1, 1))
            ),
            monthCashback = listOf(
                CashbackItem("1", "2026-10", sber, "Аптеки", 5.0),
                CashbackItem("2", "2026-10", other, "АЗС", 3.0)
            ),
            today = today
        )
        assertEquals(Money.ofMinor(100 * 100L), s.accounts)
        assertEquals(1, s.accountCount)
        assertEquals(Money.ofMinor(1200 * 100L), s.deposits)
        assertEquals(1, s.depositCount)
        assertEquals(1, s.cashbackCount)
    }

    @Test fun bankWithNothing_isEmpty() {
        assertTrue(BankSummary.of("sber", emptyList(), emptyList(), emptyList(), today).isEmpty)
    }
}
