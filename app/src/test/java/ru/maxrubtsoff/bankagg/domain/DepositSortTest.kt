package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.DepositOrder
import ru.maxrubtsoff.bankagg.domain.model.DepositSort
import ru.maxrubtsoff.bankagg.domain.model.DepositSorting
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

class DepositSortTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun d(id: String, brand: BankBrand, principal: Double, rateBp: Int, start: LocalDate, end: LocalDate) =
        Deposit(id = id, brand = brand, principal = Money.ofMajor(principal), rateBp = rateBp, startDate = start, endDate = end)

    // t: ends soonest, opened earliest; a: biggest, latest opened; o: highest rate.
    private val t = d("t", BankBrand.TBANK, 1_000_000.0, 1600, LocalDate.of(2025, 10, 30), LocalDate.of(2026, 11, 30))
    private val a = d("a", BankBrand.ALFA, 1_300_000.0, 1351, LocalDate.of(2026, 10, 2), LocalDate.of(2027, 2, 2))
    private val o = d("o", BankBrand.OZON, 500_000.0, 1800, LocalDate.of(2025, 12, 8), LocalDate.of(2027, 6, 8))
    private val all = listOf(a, o, t)

    private fun ids(order: DepositOrder) =
        DepositSorting.sort(all, order, { it }, { DepositMath.project(it, today) }).joinToString("") { it.id }

    @Test fun naturalDirections() {
        assertEquals("tao", ids(DepositOrder(DepositSort.END)))
        assertEquals("aot", ids(DepositOrder(DepositSort.START)))
        assertEquals("ato", ids(DepositOrder(DepositSort.AMOUNT)))
        assertEquals("ota", ids(DepositOrder(DepositSort.RATE)))
    }

    @Test fun gainAndIncomeFollowProjection() {
        // t has run a year at 16% on 1 000 000 — the most accrued by far.
        assertEquals('t', ids(DepositOrder(DepositSort.GAIN)).first())
        // o: 18% for 1.5 years on 500 000 (~135k) vs t: ~170k for 13 months vs a: ~59k.
        assertEquals("toa", ids(DepositOrder(DepositSort.INCOME)))
    }

    @Test fun reversedFlips() {
        assertEquals("oat", ids(DepositOrder(DepositSort.END, reversed = true)))
    }

    @Test fun pickKeepsDirection_flipReverses() {
        val start = DepositOrder(DepositSort.END)
        // Picking the same sort in the sheet changes nothing — the arrow button flips.
        assertEquals(start, start.pick(DepositSort.END))
        val flipped = start.flipped()
        assertEquals(DepositOrder(DepositSort.END, reversed = true), flipped)
        assertEquals(flipped, flipped.pick(DepositSort.END))
        // Another sort starts in its natural direction.
        assertEquals(DepositOrder(DepositSort.RATE, reversed = false), flipped.pick(DepositSort.RATE))
    }
}
