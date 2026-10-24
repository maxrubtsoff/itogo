package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Reminder
import ru.maxrubtsoff.bankagg.domain.model.ReminderSettings
import ru.maxrubtsoff.bankagg.domain.model.Reminders
import java.time.LocalDate

class RemindersTest {

    private val dep = Deposit(
        id = "a", brand = BankBrand.ALFA, principal = Money.ofMajor(1_000_000.0), rateBp = 1145,
        startDate = LocalDate.of(2026, 8, 26), endDate = LocalDate.of(2026, 10, 31)
    )
    private val on = ReminderSettings()

    private fun due(today: LocalDate, filled: Boolean = true, s: ReminderSettings = on, hidden: Boolean = false, sent: Set<String> = emptySet()) =
        Reminders.due(today, listOf(dep), filled, s, hidden, sent)

    @Test fun threeDaysBefore_andOnTheDay() {
        val before = due(LocalDate.of(2026, 10, 28)).single()
        assertEquals("dep:a:before", before.id)
        assertEquals("Вклад закрывается через 3 дня", before.title)
        assertTrue(before.text.startsWith("Альфа-Банк, 31 октября · к выдаче "))
        assertEquals("Вклад закрывается сегодня", due(LocalDate.of(2026, 10, 31)).single().title)
        assertTrue(due(LocalDate.of(2026, 10, 29)).isEmpty())
    }

    @Test fun chosenDays_andOff() {
        assertEquals(1, due(LocalDate.of(2026, 10, 24), s = on.copy(daysBefore = 7)).size)
        assertTrue(due(LocalDate.of(2026, 10, 28), s = on.copy(depositEnding = false)).isEmpty())
    }

    @Test fun toldOnce() {
        assertTrue(due(LocalDate.of(2026, 10, 28), sent = setOf("dep:a:before")).isEmpty())
    }

    @Test fun hiddenAmounts_noSums() {
        val r = due(LocalDate.of(2026, 10, 31), hidden = true).single()
        assertTrue(r.text.endsWith("•••• ₽"))
        assertFalse(r.text.any { it.isDigit() && r.text.indexOf(it) > r.text.indexOf("выдаче") })
    }

    @Test fun cashback_onlyWhenNotFilled_inTheFirstDays() {
        val nov1 = LocalDate.of(2026, 11, 1)
        val r = Reminders.due(nov1, emptyList(), cashbackFilled = false, on, false, emptySet()).single()
        assertEquals(Reminder.Kind.CASHBACK_MONTH, r.kind)
        assertEquals("Ноябрь начался", r.title)
        assertTrue(Reminders.due(nov1, emptyList(), cashbackFilled = true, on, false, emptySet()).isEmpty())
        assertTrue(Reminders.due(LocalDate.of(2026, 11, 10), emptyList(), false, on, false, emptySet()).isEmpty())
        assertTrue(Reminders.due(nov1, emptyList(), false, on, false, setOf("cb:2026-11")).isEmpty())
    }
}
