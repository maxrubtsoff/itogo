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

    // B-99: the sync works on the record as stored now.
    @Test fun syncFresh_keeps_remind_flag_changed_after_snapshot() {
        val fresh = Subscription("1", "Яндекс Плюс", 29_900, Period.MONTHLY, LocalDate.of(2026, 10, 28), payee = "YANDEX*PLUS", remind = true)
        val p = Payment("Yandex*Plus", 29_900, LocalDate.of(2026, 10, 28), tbank)
        val after = Subscriptions.syncFresh(fresh, listOf(p))!!
        assertEquals(LocalDate.of(2026, 11, 28), after.nextDate)
        assertTrue(after.remind)
    }

    @Test fun syncFresh_returns_null_when_nothing_to_do() {
        val s = Subscription("1", "Яндекс Плюс", 29_900, Period.MONTHLY, LocalDate.of(2026, 10, 28), payee = "YANDEX*PLUS")
        assertEquals(null, Subscriptions.syncFresh(s, emptyList()))
    }

    @Test fun syncFresh_does_not_touch_title_decor_status() {
        val fresh = Subscription("1", "Моя подписка", 29_900, Period.MONTHLY, LocalDate.of(2026, 10, 28), payee = "YANDEX*PLUS")
        val after = Subscriptions.syncFresh(fresh, listOf(Payment("Yandex*Plus", 39_900, LocalDate.of(2026, 10, 28), tbank)))!!
        assertEquals(fresh.copy(nextDate = after.nextDate, priceMinor = 39_900, previousPriceMinor = 29_900), after)
    }

    // B-95: the payment day survives short months.
    private fun sub(period: Period, next: LocalDate, anchor: Int? = null) =
        Subscription("1", "S", 10_000, period, next, payee = "S", anchorDay = anchor)

    @Test fun monthly_31st_upcoming_keeps_day_through_short_months() {
        val s = sub(Period.MONTHLY, LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), Subscriptions.upcoming(s, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), Subscriptions.upcoming(s, LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2026, 4, 30), Subscriptions.upcoming(s, LocalDate.of(2026, 4, 1)))
        assertEquals(LocalDate.of(2026, 5, 31), Subscriptions.upcoming(s, LocalDate.of(2026, 5, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), Subscriptions.upcoming(s, LocalDate.of(2028, 2, 1)))
    }

    @Test fun monthly_30th_clamps_in_february_and_returns() {
        val s = sub(Period.MONTHLY, LocalDate.of(2026, 1, 30))
        assertEquals(LocalDate.of(2026, 2, 28), Subscriptions.upcoming(s, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 30), Subscriptions.upcoming(s, LocalDate.of(2026, 3, 1)))
    }

    @Test fun paid_in_short_month_returns_to_anchor_day() {
        val s = sub(Period.MONTHLY, LocalDate.of(2026, 2, 28), anchor = 31)
        val after = Subscriptions.paid(s, Payment("S", 10_000, LocalDate.of(2026, 2, 28), tbank))
        assertEquals(LocalDate.of(2026, 3, 31), after.nextDate)
    }

    @Test fun yearly_leap_day_returns_to_29_feb() {
        val s = sub(Period(PeriodUnit.YEAR), LocalDate.of(2028, 2, 29))
        assertEquals(LocalDate.of(2029, 2, 28), Subscriptions.upcoming(s, LocalDate.of(2028, 3, 1)))
        assertEquals(LocalDate.of(2032, 2, 29), Subscriptions.upcoming(s, LocalDate.of(2031, 3, 1)))
    }

    @Test fun reanchor_keeps_anchor_when_date_unchanged_and_resets_on_edit() {
        val old = sub(Period.MONTHLY, LocalDate.of(2026, 2, 28), anchor = 31)
        assertEquals(31, Subscriptions.reanchor(old, old.copy(title = "New")).anchorDay)
        assertEquals(15, Subscriptions.reanchor(old, old.copy(nextDate = LocalDate.of(2026, 3, 15))).anchorDay)
        assertEquals(10, Subscriptions.reanchor(null, sub(Period.MONTHLY, LocalDate.of(2026, 3, 10))).anchorDay)
    }
}
