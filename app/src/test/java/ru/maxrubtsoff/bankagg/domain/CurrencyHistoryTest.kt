package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CurrencyHistory
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate
import java.time.ZoneId

class CurrencyHistoryTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 10)
    private val all = CapitalPart.entries.toSet()
    private val buyDay = LocalDate.of(2026, 5, 30)

    private fun point(d: LocalDate, currency: Long, accounts: Long = 100_00L) =
        CapitalPoint(d, accounts + currency, mapOf(CapitalPart.ACCOUNTS to accounts, CapitalPart.CURRENCY to currency))

    private val buy = FxOperation("1", "USD", FxKind.BUY, buyDay, 1_000_000L, 76_500_000L, 0L)
    private val rates = listOf(
        FxRate("USD", LocalDate.of(2026, 5, 29), 80_000_000L),
        FxRate("USD", LocalDate.of(2026, 6, 2), 82_000_000L)
    )

    private fun run(saved: List<CapitalPoint>, ops: List<FxOperation> = listOf(buy), r: List<FxRate> = rates, accounts: List<Account> = emptyList()) =
        CurrencyHistory.rebuild(saved, accounts, emptyList(), ops, r, all, today, zone).associateBy { it.date }

    @Test fun daysBeforeFirstDealAreZero() {
        val d = LocalDate.of(2024, 8, 1)
        val out = run(listOf(point(d, 710_226_00L)))
        assertEquals(0L, out.getValue(d).parts[CapitalPart.CURRENCY])
        assertEquals(100_00L, out.getValue(d).totalMinor)
    }

    @Test fun afterBuyIsAmountTimesRateOfDay() {
        val d1 = buyDay; val d2 = LocalDate.of(2026, 6, 3)
        val out = run(listOf(point(d1, 0L), point(d2, 123L)))
        assertEquals(800_000_00L, out.getValue(d1).parts[CapitalPart.CURRENCY]) // 10 000 × 80 (rate of 29.05)
        assertEquals(820_000_00L, out.getValue(d2).parts[CapitalPart.CURRENCY])
        assertEquals(820_000_00L + 100_00L, out.getValue(d2).totalMinor)
    }

    @Test fun noRatesUsesDealRate() {
        val out = run(listOf(point(buyDay, 0L)), r = emptyList())
        assertEquals(765_000_00L, out.getValue(buyDay).parts[CapitalPart.CURRENCY])
    }

    @Test fun sellReducesPosition() {
        val sell = FxOperation("2", "USD", FxKind.SELL, LocalDate.of(2026, 6, 3), 400_000L, 0L, 0L)
        val d = LocalDate.of(2026, 6, 4)
        val out = run(listOf(point(d, 0L)), ops = listOf(buy, sell))
        assertEquals(6_000L * 82_00L, out.getValue(d).parts[CapitalPart.CURRENCY])
    }

    @Test fun foreignAccountOnlyFromKnownDay() {
        val known = LocalDate.of(2026, 6, 1)
        val acc = Account("bsb", BankBrand.SBER, "BSB", Money.ofMinor(91_28L), "USD",
            known.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L)
        val before = LocalDate.of(2026, 5, 20); val after = LocalDate.of(2026, 6, 5)
        val out = run(listOf(point(before, 5L), point(after, 0L)), ops = emptyList(), accounts = listOf(acc))
        assertEquals(0L, out.getValue(before).parts[CapitalPart.CURRENCY])
        assertEquals(7_484_96L, out.getValue(after).parts[CapitalPart.CURRENCY]) // 91,28 × 82
    }

    @Test fun todayAndUnchangedDaysUntouched() {
        val out = run(listOf(point(today, 999L), point(LocalDate.of(2024, 1, 1), 0L)))
        assertTrue(out.isEmpty())
    }
}
