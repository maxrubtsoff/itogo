package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Payment
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.PeriodUnit
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.Subscriptions
import java.time.LocalDate

/** F-27: subscriptions found in repeated payments, kept up to date by new ones. */
class SubscriptionsTest {

    private val today = LocalDate.of(2026, 10, 22)
    private val tbank = BankBrand.TBANK
    private fun pay(payee: String, rub: Long, date: String) = Payment(payee, rub * 100, LocalDate.parse(date), tbank)

    @Test fun monthlyRun_isFound_withNextDate() {
        val found = Subscriptions.find(
            listOf(pay("OKKO.TV", 349, "2026-08-21"), pay("OKKO.TV", 399, "2026-09-21"), pay("OKKO.TV", 399, "2026-10-21")),
            known = emptySet(), today = today
        ).single()
        assertEquals(PeriodUnit.MONTH, found.period.unit)
        assertEquals(39_900L, found.priceMinor)
        assertEquals(LocalDate.of(2026, 11, 21), found.nextDate)
        assertEquals("Okko.tv", found.title)
    }

    @Test fun yearly_andWeekly() {
        val y = Subscriptions.find(listOf(pay("ICLOUD", 1490, "2025-10-01"), pay("ICLOUD", 1490, "2026-10-01")), emptySet(), today).single()
        assertEquals(PeriodUnit.YEAR, y.period.unit)
        val w = Subscriptions.find(listOf(pay("GYM", 500, "2026-10-01"), pay("GYM", 500, "2026-10-08"), pay("GYM", 500, "2026-10-15")), emptySet(), today).single()
        assertEquals(PeriodUnit.WEEK, w.period.unit)
    }

    @Test fun shopVisits_areNotASubscription() {
        val shop = listOf(pay("PYATEROCHKA", 631, "2026-10-11"), pay("PYATEROCHKA", 600, "2026-10-14"), pay("PYATEROCHKA", 640, "2026-10-20"))
        assertTrue(Subscriptions.find(shop, emptySet(), today).isEmpty())
        val restaurant = listOf(pay("MANGAL", 2870, "2026-09-11"), pay("MANGAL", 10430, "2026-10-11"))
        assertTrue(Subscriptions.find(restaurant, emptySet(), today).isEmpty())
        assertTrue(Subscriptions.find(listOf(pay("LAFA", 1340, "2026-10-16")), emptySet(), today).isEmpty())
    }

    @Test fun knownOrIgnored_payee_isSkipped_andOldRunsAreGone() {
        val run = listOf(pay("OKKO.TV", 399, "2026-09-21"), pay("OKKO.TV", 399, "2026-10-21"))
        assertTrue(Subscriptions.find(run, setOf("OKKO.TV"), today).isEmpty())
        val old = listOf(pay("IVI", 399, "2025-01-10"), pay("IVI", 399, "2025-02-10"))
        assertTrue(Subscriptions.find(old, emptySet(), today).isEmpty())
    }

    @Test fun paymentMovesTheDate_andRemembersARaise() {
        val s = Subscription("1", "Яндекс Плюс", 29_900, Period.MONTHLY, LocalDate.of(2026, 10, 28), payee = "YANDEX*PLUS")
        val p = Payment("Yandex*Plus", 39_900, LocalDate.of(2026, 10, 28), tbank)
        assertTrue(Subscriptions.matches(s, p))
        val after = Subscriptions.paid(s, p)
        assertEquals(LocalDate.of(2026, 11, 28), after.nextDate)
        assertEquals(10_000L, after.raisedBy)
        assertFalse(Subscriptions.matches(s, p.copy(amountMinor = 150_000)))
    }

    @Test fun upcomingNeverInThePast_andMonthlyTotal() {
        val s = Subscription("1", "Кинопоиск", 39_900, Period.MONTHLY, LocalDate.of(2026, 9, 25))
        assertEquals(LocalDate.of(2026, 10, 25), Subscriptions.upcoming(s, today))
        val year = s.copy(id = "2", priceMinor = 120_000, period = Period(PeriodUnit.YEAR))
        assertEquals(39_900L + 10_000L, Subscriptions.perMonth(listOf(s, year)))
        assertNull(s.raisedBy)
        assertEquals("через 3 дня", Subscriptions.whenText(today.plusDays(3), today))
        assertEquals("завтра", Subscriptions.whenText(today.plusDays(1), today))
    }

    @Test fun syncIsIdempotent_andIgnoresOldPayments() {
        val s = Subscription("1", "Okko", 39_900, Period.MONTHLY, LocalDate.of(2026, 11, 21), payee = "OKKO.TV")
        val all = listOf(pay("OKKO.TV", 349, "2026-08-21"), pay("OKKO.TV", 399, "2026-10-21"), pay("OKKO.TV", 449, "2026-11-21"))
        val once = Subscriptions.sync(s, all)
        assertEquals(LocalDate.of(2026, 12, 21), once.nextDate)
        assertEquals(44_900L, once.priceMinor)
        assertEquals(once, Subscriptions.sync(once, all))
        assertEquals(3, Subscriptions.history(once, all).size)
    }
}
