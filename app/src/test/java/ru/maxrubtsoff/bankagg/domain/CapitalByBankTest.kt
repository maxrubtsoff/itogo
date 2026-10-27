package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalCalculator
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.PeriodUnit
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.usecase.CapitalByBank
import java.time.LocalDate

class CapitalByBankTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val a = BankBrand("a", "Альфа", 0xFF111111)
    private val b = BankBrand("b", "Бета", 0xFF222222)
    private val c = BankBrand("c", "Гамма", 0xFF333333)

    private fun acc(id: String, brand: BankBrand, rub: Long, excluded: Boolean = false) =
        Account(id, brand, id, Money.ofMinor(rub * 100), "RUB", 0L, excluded)

    private fun snap(accounts: List<Account>, cash: Long? = null) = CapitalCalculator.compute(
        accounts, emptyList(), emptyList(), emptyList(),
        cash?.let { CashBalance(Money.ofMinor(it * 100), 1L) }, CapitalPart.entries.toSet(), today
    )

    @Test fun emptyBanksAreLeftOut() {
        val s = CapitalByBank.slice(snap(listOf(acc("1", a, 100), acc("2", b, 0), acc("3", c, 50, excluded = true))), emptyList(), emptyList())
        assertEquals(listOf("a"), s.map { it.key })
    }

    @Test fun sortedByValueDescending() {
        val s = CapitalByBank.slice(snap(listOf(acc("1", a, 100), acc("2", b, 300), acc("3", c, 200))), emptyList(), emptyList())
        assertEquals(listOf("b", "c", "a"), s.map { it.key })
    }

    @Test fun restIsTotalMinusBanks() {
        val sn = snap(listOf(acc("1", a, 100), acc("2", b, 300)), cash = 40)
        val s = CapitalByBank.slice(sn, emptyList(), emptyList())
        assertNull(s.last().brand)
        assertEquals(4000L, s.last().valueMinor)
        assertEquals(sn.totalMinor, s.sumOf { it.valueMinor })
    }

    @Test fun noRestWhenEverythingIsInBanks() {
        val s = CapitalByBank.slice(snap(listOf(acc("1", a, 100), acc("2", b, 300))), emptyList(), emptyList())
        assertTrue(s.none { it.brand == null })
        assertTrue(CapitalByBank.worthShowing(s))
    }

    @Test fun oneBankIsNotWorthASwitch() {
        val s = CapitalByBank.slice(snap(listOf(acc("1", a, 100)), cash = 10), emptyList(), emptyList())
        assertFalse(CapitalByBank.worthShowing(s))
    }

    @Test fun negativeBankHasNoShareAndRestStaysConsistent() {
        val sn = snap(listOf(acc("1", a, 300), acc("2", b, -100)), cash = 40)
        val s = CapitalByBank.slice(sn, emptyList(), emptyList())
        assertEquals(0f, s.first { it.key == "b" }.share, 0f)
        assertEquals(sn.totalMinor, s.sumOf { it.valueMinor })
        assertNull(s.last().brand)
        assertEquals(4000L, s.last().valueMinor)
    }

    @Test fun linksShowOnlyWhereThereIsData() {
        val sub = Subscription("s", "Кино", 100, Period(PeriodUnit.MONTH), today, brand = a)
        val cb = CashbackItem("i", "2026-03", b, "Такси", 5.0)
        val s = CapitalByBank.slice(snap(listOf(acc("1", a, 100), acc("2", b, 300))), listOf(sub), listOf(cb))
        assertTrue(s.first { it.key == "a" }.hasSubscriptions)
        assertFalse(s.first { it.key == "a" }.hasCashback)
        assertTrue(s.first { it.key == "b" }.hasCashback)
    }
}
