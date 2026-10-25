package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.ReminderSettings
import ru.maxrubtsoff.bankagg.domain.model.Reminders
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import java.time.LocalDate

class ReminderSettingsTest {

    private val off = ReminderSettings(newBank = false, depositEnding = false, cashbackMonth = false, subscriptions = false)

    @Test fun any_falseOnlyWhenAllOff() {
        assertFalse(off.any)
        assertTrue(off.copy(newBank = true).any)
        assertTrue(off.copy(depositEnding = true).any)
        assertTrue(off.copy(cashbackMonth = true).any)
        assertTrue(off.copy(subscriptions = true).any)
    }

    @Test fun subscriptionsOff_suppressesSubscriptions_notCashback() {
        val sub = Subscription("s1", "Netflix", 59_900, Period.MONTHLY, LocalDate.of(2026, 11, 2), remind = true)
        val today = LocalDate.of(2026, 11, 1)
        val s = ReminderSettings(subscriptions = false)
        val out = Reminders.due(today, emptyList(), false, s, false, emptySet(), listOf(sub))
        assertTrue(out.none { it.kind == ru.maxrubtsoff.bankagg.domain.model.Reminder.Kind.SUBSCRIPTION })
        assertTrue(out.any { it.kind == ru.maxrubtsoff.bankagg.domain.model.Reminder.Kind.CASHBACK_MONTH })
        assertTrue(Reminders.due(today, emptyList(), true, ReminderSettings(), false, emptySet(), listOf(sub)).isNotEmpty())
    }
}
