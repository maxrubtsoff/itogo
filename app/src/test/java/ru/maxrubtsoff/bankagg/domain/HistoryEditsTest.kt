package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPast
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapitalShift
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.HistoryEdits
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.NoteHighlight
import ru.maxrubtsoff.bankagg.domain.model.OperationForm
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import java.time.LocalDate
import java.time.ZoneOffset

/** F-51: what correcting and deleting an operation does to the balance, the chart and the cash. */
class HistoryEditsTest {

    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 20)
    private fun ms(d: LocalDate, hour: Int = 12) = d.atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    private fun account(balance: Long, checkedAt: Long?, estimated: Boolean) =
        Account("a", BankBrand.SBER, "Счёт", Money.ofMinor(balance), "RUB", 0L, checkedAt = checkedAt, estimated = estimated)

    private fun tx(amount: Long, at: Long) =
        Transaction("t", BankBrand.SBER, "a", Money.ofMinor(amount), "RUB", if (amount >= 0) TransactionKind.INCOME else TransactionKind.EXPENSE, null, null, at)

    @Test fun `an operation inside the banks balance holds nothing`() {
        val acc = account(10_000, checkedAt = ms(day), estimated = false)
        val t = tx(2_026_000_000, ms(day))
        assertFalse(HistoryEdits.counted(t, acc))
        assertNull(HistoryEdits.shiftOf(t, acc, emptyList(), zone))
        assertEquals(10_000, HistoryEdits.balanceAfter(acc, t, null))
    }

    @Test fun `an operation counted on top of the balance moves it and the chart`() {
        val acc = account(15_000, checkedAt = ms(day), estimated = true)
        val t = tx(5_000, ms(day.plusDays(1)))
        assertTrue(HistoryEdits.counted(t, acc))
        assertEquals(10_000, HistoryEdits.balanceAfter(acc, t, null))
        assertEquals(CapitalShift(CapitalPart.ACCOUNTS, day.plusDays(1), 5_000), HistoryEdits.shiftOf(t, acc, emptyList(), zone))
        // Corrected 5 000 → 7 000 in place: the balance follows by the difference.
        assertEquals(17_000, HistoryEdits.balanceAfter(acc, t, t.copy(amount = Money.ofMinor(7_000))))
    }

    @Test fun `an excluded account holds nothing in the chart`() {
        val acc = account(15_000, ms(day), true).copy(excluded = true)
        assertNull(HistoryEdits.shiftOf(tx(5_000, ms(day.plusDays(1))), acc, emptyList(), zone))
    }

    @Test fun `direction flips the kind only when the sign changes`() {
        val old = tx(-5_000, ms(day))
        val same = HistoryEdits.transaction(old, OperationForm(false, 6_000, day), null, zone)
        assertEquals(TransactionKind.EXPENSE, same.kind)
        assertEquals(-6_000, same.amount.minor)
        val flipped = HistoryEdits.transaction(old, OperationForm(true, 6_000, day.minusDays(2)), null, zone)
        assertEquals(TransactionKind.INCOME, flipped.kind)
        assertEquals(old.postedAt - 2 * 86_400_000L, flipped.postedAt)
    }

    @Test fun `took and gave back is one pair`() {
        val take = CashChange(ms(day, 10), Money.ofMinor(0), Money.ofMinor(-100_000))
        val back = CashChange(ms(day, 15), Money.ofMinor(100_000), Money.ofMinor(100_000))
        val other = CashChange(ms(day.plusDays(1)), Money.ofMinor(0), Money.ofMinor(100_000))
        assertEquals(back, HistoryEdits.cashPartner(listOf(take, back, other), take.atMs, zone))
        assertNull(HistoryEdits.cashPartner(listOf(take, other), take.atMs, zone))
    }

    @Test fun `a cash edit keeps its hour when the day moves`() {
        val old = CashChange(ms(day, 10), Money.ofMinor(300), Money.ofMinor(100))
        val next = HistoryEdits.cashChange(old, OperationForm(false, 50, day.minusDays(1)), zone)
        assertEquals(ms(day.minusDays(1), 10), next.atMs)
        assertEquals(-50, next.delta.minor)
        assertEquals(150, next.amount.minor)
    }

    @Test fun `the preview shows the change only when there is one`() {
        val parts = mapOf(CapitalPart.CASH to 5_000L)
        val p = HistoryEdits.preview(CapitalPart.CASH, -1_000, day, parts, 20_000, setOf(CapitalPart.CASH))
        assertEquals(4_000, p.partNow)
        assertEquals(19_000, p.capitalNow)
        assertEquals(day, p.from)
        assertNull(HistoryEdits.preview(CapitalPart.CASH, 0, day, parts, 20_000, setOf(CapitalPart.CASH)).from)
        assertEquals(20_000, HistoryEdits.preview(CapitalPart.CASH, -1_000, day, parts, 20_000, emptySet()).capitalNow)
    }

    @Test fun `shifts move the saved days from their date on and give the day a point`() {
        val today = LocalDate.of(2026, 10, 25)
        fun p(d: LocalDate, cash: Long) = CapitalPoint(d, cash + 100, mapOf(CapitalPart.CASH to cash, CapitalPart.ACCOUNTS to 100L))
        val points = listOf(p(day.minusDays(5), 1_000), p(day.plusDays(2), 3_000))
        val out = CapitalPast.withShifts(points, listOf(CapitalShift(CapitalPart.CASH, day, -500)), setOf(CapitalPart.CASH, CapitalPart.ACCOUNTS), today)
            .associateBy { it.date }
        // The day before the shift stays; the day of the shift is new, the later one moves.
        assertFalse(out.containsKey(day.minusDays(5)))
        assertEquals(500L, out.getValue(day).parts[CapitalPart.CASH])
        assertEquals(2_500L, out.getValue(day.plusDays(2)).parts[CapitalPart.CASH])
        assertEquals(2_600L, out.getValue(day.plusDays(2)).totalMinor)
    }

    @Test fun `the amount in a bank text is found with its currency sign`() {
        val text = "Перевод 20 260 000 ₽ от Ивана. Баланс 5 000 ₽"
        val r = NoteHighlight.amountRange(text, 2_026_000_000)!!
        assertEquals("20 260 000 ₽", text.substring(r))
        val c = "Покупка 1 284,50 р. Остаток 10 000 р."
        assertEquals("1 284,50 р.", c.substring(NoteHighlight.amountRange(c, -128_450)!!))
        assertNull(NoteHighlight.amountRange(c, 777))
    }
}
