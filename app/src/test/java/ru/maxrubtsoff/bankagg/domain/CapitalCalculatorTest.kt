package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalCalculator
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

class CapitalCalculatorTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val all = CapitalPart.entries.toSet()

    private fun account(brand: BankBrand, ccy: String, major: Long) =
        Account("${brand.key}:$ccy", brand, brand.title, Money.ofMinor(major * 100), ccy, 0L)

    private fun deposit(major: Long, start: LocalDate, end: LocalDate) = Deposit(
        id = "d$major", brand = BankBrand.SBER, principal = Money.ofMinor(major * 100), rateBp = 0,
        startDate = start, endDate = end, exitAmount = Money.ofMinor(major * 100)
    )

    private fun usdRate(rub: Long) = FxRate("USD", today, rub * 1_000_000)
    private fun buyUsd(units: Long, rubMajor: Long) =
        FxOperation("b$units", "USD", FxKind.BUY, today.minusYears(1), units * 100, rubMajor * 100, 0)

    @Test fun excludedAccount_listedButNotSummed() {
        val kept = account(BankBrand.SBER, "RUB", 100_000)
        val left = account(BankBrand.TBANK, "RUB", 40_000).copy(excluded = true)
        val snap = CapitalCalculator.compute(listOf(kept, left), emptyList(), emptyList(), emptyList(), null, all, today)
        assertEquals(100_000L * 100, snap.totalMinor)
        assertEquals(2, snap.rubAccounts.size)
    }

    @Test fun sums_four_parts() {
        val snap = CapitalCalculator.compute(
            accounts = listOf(account(BankBrand.SBER, "RUB", 100_000)),
            deposits = listOf(deposit(200_000, today.minusMonths(1), today.plusMonths(5))),
            fxOps = listOf(buyUsd(1000, 76_500)),
            rates = listOf(usdRate(80)),
            cash = CashBalance(Money.ofMinor(50_000_00), 1L),
            included = all,
            today = today
        )
        assertEquals((100_000L + 200_000 + 80_000 + 50_000) * 100, snap.totalMinor)
        assertEquals(350_000L, snap.currency.unrealizedMinor)          // +3 500 ₽
        assertEquals(1f, snap.parts.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test fun excluded_part_is_out_of_total_and_bar_but_still_computed() {
        val snap = CapitalCalculator.compute(
            listOf(account(BankBrand.SBER, "RUB", 100_000)), emptyList(), emptyList(), emptyList(),
            CashBalance(Money.ofMinor(10_000_00), 1L), all - CapitalPart.CASH, today
        )
        assertEquals(10_000_000L, snap.totalMinor)
        val cash = snap.part(CapitalPart.CASH)
        assertFalse(cash.included)
        assertEquals(1_000_000L, cash.valueMinor)
        assertEquals(0f, cash.share)
        assertEquals(1f, snap.part(CapitalPart.ACCOUNTS).share)
    }

    @Test fun foreign_accounts_go_to_currency_at_cbr_rate() {
        val byn = FxRate("BYN", today, 27_737_500)
        val snap = CapitalCalculator.compute(
            listOf(account(BankBrand.SBER, "RUB", 1_000), account(BankBrand.BSB, "BYN", 100)),
            emptyList(), emptyList(), listOf(byn), null, all, today
        )
        assertEquals(100_000L, snap.part(CapitalPart.ACCOUNTS).valueMinor)
        assertEquals(277_375L, snap.part(CapitalPart.CURRENCY).valueMinor)   // 100 Br × 27,7375
        assertEquals(1, snap.rubAccounts.size)
        assertEquals(1, snap.currency.holdings.size)
    }

    @Test fun finished_deposit_is_not_counted_twice() {
        val done = deposit(500_000, today.minusYears(1), today.minusDays(1))
        val lastDay = deposit(300_000, today.minusYears(1), today)
        val snap = CapitalCalculator.compute(emptyList(), listOf(done, lastDay), emptyList(), emptyList(), null, all, today)
        assertEquals(30_000_000L, snap.part(CapitalPart.DEPOSITS).valueMinor)
    }

    @Test fun currency_without_any_rate_is_left_out_and_reported() {
        val cny = FxOperation("c", "CNY", FxKind.BUY, today, 1_000_00, 11_000_00, 0)
        val snap = CapitalCalculator.compute(
            emptyList(), emptyList(), listOf(buyUsd(10, 800), cny), listOf(usdRate(80)), null, all, today
        )
        assertEquals(listOf("CNY"), snap.currency.missingRates)
        assertEquals(80_000L, snap.part(CapitalPart.CURRENCY).valueMinor)
    }

    @Test fun empty_parts_have_no_data() {
        val snap = CapitalCalculator.compute(emptyList(), emptyList(), emptyList(), emptyList(), null, all, today)
        assertTrue(snap.parts.none { it.hasData })
        assertEquals(0L, snap.totalMinor)
    }
}
