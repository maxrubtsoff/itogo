package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSlice
import ru.maxrubtsoff.bankagg.domain.model.CapitalCalculator
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapitalSummary
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

class CapitalSummaryTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val a = BankBrand("a", "Альфа", 0xFF111111)

    private fun acc(id: String, rub: Long, excluded: Boolean = false) =
        Account(id, a, id, Money.ofMinor(rub * 100), "RUB", 0L, excluded)

    private fun snap(accounts: List<Account>, included: Set<CapitalPart> = CapitalPart.entries.toSet()) = CapitalCalculator.compute(
        accounts, emptyList(), emptyList(), emptyList(), CashBalance(Money.ofMinor(500), 1L), included, today
    )

    @Test fun accountsCountLeavesOutExcludedOnes() {
        val s = snap(listOf(acc("1", 10), acc("2", 20), acc("3", 30, excluded = true)))
        val line = CapitalSummary.part(s, s.part(CapitalPart.ACCOUNTS), mapOf(CapitalPart.ACCOUNTS to -700L))
        assertEquals("2 счёта", line.detail)
        assertEquals(-700L, line.moveMinor)
        assertFalse(line.perMonth)
    }

    @Test fun excludedPartSaysSoAndShowsNoMove() {
        val s = snap(listOf(acc("1", 10)), included = setOf(CapitalPart.CASH))
        val line = CapitalSummary.part(s, s.part(CapitalPart.ACCOUNTS), mapOf(CapitalPart.ACCOUNTS to 100L))
        assertEquals("не в итоге", line.detail)
        assertNull(line.moveMinor)
    }

    @Test fun cashHasAnEmptyDetailButKeepsItsMove() {
        val s = snap(emptyList())
        val line = CapitalSummary.part(s, s.part(CapitalPart.CASH), mapOf(CapitalPart.CASH to 300L))
        assertEquals("", line.detail)
        assertEquals(300L, line.moveMinor)
    }

    @Test fun partMovesAreTheLast30DaysAndSkipZero() {
        val points = listOf(
            CapitalPoint(today.minusDays(40), 0, mapOf(CapitalPart.ACCOUNTS to 100L, CapitalPart.CASH to 50L)),
            CapitalPoint(today.minusDays(10), 0, mapOf(CapitalPart.ACCOUNTS to 400L, CapitalPart.CASH to 50L)),
            CapitalPoint(today, 0, mapOf(CapitalPart.ACCOUNTS to 250L, CapitalPart.CASH to 50L))
        )
        val moves = CapitalSummary.partMoves(points, today)
        assertEquals(150L, moves[CapitalPart.ACCOUNTS])
        assertFalse(CapitalPart.CASH in moves)
    }

    @Test fun noHistoryNoMoves() {
        assertTrue(CapitalSummary.partMoves(emptyList(), today).isEmpty())
    }

    @Test fun bankShowsWhatItHoldsAndDepositIncomePerMonth() {
        val slice = BankSlice(
            brand = a, valueMinor = 1, share = 1f,
            accounts = List(5) { BankSlice.Line("a", 0) }, deposits = List(2) { BankSlice.Line("d", 0) },
            subscriptionsCount = 3, depositsMonthlyMinor = 12_000
        )
        val line = CapitalSummary.bank(slice)
        assertEquals("5 счетов · 2 вклада", line.detail)
        assertEquals(12_000L, line.moveMinor)
        assertTrue(line.perMonth)
    }

    @Test fun bankWithoutDepositsHasNoMove() {
        val line = CapitalSummary.bank(BankSlice(brand = a, valueMinor = 1, share = 1f, accounts = listOf(BankSlice.Line("a", 0))))
        assertEquals("1 счёт", line.detail)
        assertNull(line.moveMinor)
    }

    @Test fun restSaysWhatIsOutsideBanks() {
        val rest = BankSlice(brand = null, valueMinor = 10, share = 0f, restCashMinor = 6, restCurrencyMinor = 4)
        assertEquals("наличные · валюта", CapitalSummary.bank(rest).detail)
        assertEquals("наличные", CapitalSummary.bank(rest.copy(restCurrencyMinor = 0)).detail)
    }
}
