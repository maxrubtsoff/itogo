package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSpend
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Spending
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class SpendingTest {

    private val utc = ZoneOffset.UTC
    private val nov = YearMonth.of(2026, 11)
    private val tb = Account("tb", BankBrand.TBANK, "Т-Банк", Money.ofMinor(0), "RUB", 0L)
    private val sb = Account("sb", BankBrand.SBER, "Сбер", Money.ofMinor(0), "RUB", 0L)
    private val off = Account("off", BankBrand.ALFA, "Альфа", Money.ofMinor(0), "RUB", 0L, excluded = true)
    private val accounts = listOf(tb, sb, off)

    private fun at(day: Int, h: Int = 12, month: Int = 11) =
        LocalDateTime.of(2026, month, day, h, 0).toInstant(utc).toEpochMilli()

    private fun op(
        id: String, acc: Account, minor: Long, kind: TransactionKind = TransactionKind.EXPENSE,
        who: String? = null, ms: Long = at(5), cur: String = "RUB"
    ) = Transaction(id, acc.brand, acc.id, Money.ofMinor(minor), cur, kind, null, who, ms)

    private fun month(vararg t: Transaction, own: Set<String> = emptySet(), with: Boolean = false, m: YearMonth = nov, zone: ZoneId = utc) =
        Spending.month(t.toList(), accounts, own, m, with, zone)

    @Test fun totalExcludesOwnTransfers() {
        val t = arrayOf(
            op("a", tb, -1000), op("o", tb, -5000, TransactionKind.TRANSFER), op("i", sb, 5000, TransactionKind.INCOME)
        )
        assertEquals(1000L, month(*t, own = setOf("o", "i")).totalMinor)
        assertEquals(1000L, month(*t, own = setOf("o", "i"), with = true).totalMinor)
        assertEquals(0L, month(*t, own = setOf("o", "i")).transfersMinor)
        assertEquals(0L, month(*t, own = setOf("o", "i")).incomeMinor)
    }

    @Test fun transfersToPeopleFollowSwitch() {
        val t = arrayOf(op("a", tb, -1000), op("p", tb, -3000, TransactionKind.TRANSFER, who = "Иван"))
        val off = month(*t)
        assertEquals(1000L, off.totalMinor); assertEquals(3000L, off.transfersMinor); assertEquals(1, off.transfersCount)
        val on = month(*t, with = true)
        assertEquals(4000L, on.totalMinor); assertEquals(1000L, on.purchasesMinor)
        // The banks' rows do not move with the switch.
        assertEquals(off.byBank, on.byBank)
    }

    @Test fun incomeAndRefundsIgnored() {
        val r = month(op("a", tb, -1000), op("r", tb, 400, TransactionKind.INCOME))
        assertEquals(1000L, r.totalMinor)
        assertEquals(400L, r.incomeMinor)
    }

    @Test fun foreignCurrencyIgnored() {
        assertEquals(0L, month(op("a", tb, -1000, cur = "USD")).totalMinor)
    }

    @Test fun excludedAccountIgnored() {
        assertEquals(0L, month(op("a", off, -1000)).totalMinor)
    }

    @Test fun otherMonthIgnored() {
        assertEquals(0L, month(op("a", tb, -1000, ms = at(5, month = 10))).totalMinor)
    }

    @Test fun monthBoundaryByZone() {
        val msk = ZoneId.of("Europe/Moscow")
        // 31 October 23:30 MSK is 20:30 UTC: October at home, still October by UTC.
        val late = LocalDateTime.of(2026, 10, 31, 23, 30).atZone(msk).toInstant().toEpochMilli()
        val t = op("a", tb, -1000, ms = late)
        assertEquals(1000L, month(t, m = YearMonth.of(2026, 10), zone = msk).totalMinor)
        assertEquals(0L, month(t, m = nov, zone = msk).totalMinor)
        // 1 November 01:00 MSK is 31 October 22:00 UTC: November at home.
        val early = LocalDateTime.of(2026, 11, 1, 1, 0).atZone(msk).toInstant().toEpochMilli()
        assertEquals(1000L, month(op("b", tb, -1000, ms = early), zone = msk).totalMinor)
    }

    @Test fun byBankSortedDescWithCounts() {
        val r = month(op("a", tb, -1000), op("b", sb, -3000), op("c", sb, -500), op("d", tb, -200))
        assertEquals(listOf(BankSpend(BankBrand.SBER, 3500, 2), BankSpend(BankBrand.TBANK, 1200, 2)), r.byBank)
        assertEquals(4, r.count)
    }

    @Test fun foldKeepsFourAndRestPastFive() {
        fun b(n: Int) = BankSpend(BankBrand.SBER, (100 - n).toLong(), 1)
        val five = Spending.fold((1..5).map(::b))
        assertEquals(5, five.shown.size); assertTrue(five.rest.isEmpty())
        val seven = Spending.fold((1..7).map(::b))
        assertEquals(4, seven.shown.size); assertEquals(3, seven.rest.size)
    }

    @Test fun windowIsSixMonthsEndingToday() {
        val w = Spending.window(LocalDate.of(2026, 11, 9), 6)
        assertEquals(YearMonth.of(2026, 6), w.first())
        assertEquals(nov, w.last())
        assertEquals(6, w.size)
    }

    @Test fun hasAnySpending() {
        assertFalse(Spending.hasAny(listOf(op("i", tb, 500, TransactionKind.INCOME)), accounts, emptySet()))
        assertTrue(Spending.hasAny(listOf(op("a", tb, -500, ms = at(1, month = 3))), accounts, emptySet()))
    }
}
