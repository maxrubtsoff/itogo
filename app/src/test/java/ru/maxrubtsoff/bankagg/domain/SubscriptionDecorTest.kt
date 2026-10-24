package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.domain.model.Payment
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.PeriodUnit
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.Subscriptions
import java.time.LocalDate

/** F-33: a subscription has its own look; the bank is only where it is charged from. */
class SubscriptionDecorTest {

    private val tbank = BankBrand.TBANK
    private fun pay(date: String) = Payment("OKKO.TV", 39_900, LocalDate.parse(date), tbank)

    @Test fun fromFinding_fillsTheLookByTitle_andKeepsTheBank() {
        val f = Subscriptions.find(
            listOf(pay("2026-09-21"), pay("2026-10-21")), emptySet(), LocalDate.of(2026, 10, 22)
        ).single()
        val s = Subscriptions.fromFinding(f, "1", 0L)
        assertEquals(BrandDecor.defaultFor("Okko.tv"), s.decor)
        assertEquals(tbank, s.brand)
        assertEquals("OKKO.TV", s.payee)
    }

    @Test fun look_givesTheDefault_whenNotTouched() {
        val s = Subscription("1", "Netflix", 100, Period(PeriodUnit.MONTH), LocalDate.of(2026, 11, 1))
        assertNull(s.decor)
        assertEquals(BrandDecor.defaultFor("Netflix"), s.look)
    }

    @Test fun look_prefersTheUsersDecor() {
        val mine = BrandDecor(0xFF123456, "НФ", 0xFFFFFFFF, "/x.png")
        val s = Subscription("1", "Netflix", 100, Period(PeriodUnit.MONTH), LocalDate.of(2026, 11, 1), decor = mine)
        assertEquals(mine, s.look)
    }

    @Test fun copy_paidAndSync_keepTheLook() {
        val mine = BrandDecor(0xFF123456, "НФ")
        val s = Subscription("1", "Okko", 29_900, Period.MONTHLY, LocalDate.of(2026, 10, 21), payee = "OKKO.TV", decor = mine)
        assertEquals(mine, s.copy(remind = true).decor)
        val paid = Subscriptions.paid(s, Payment("OKKO.TV", 39_900, LocalDate.of(2026, 10, 21), tbank))
        assertEquals(mine, paid.decor)
        assertEquals(39_900L, paid.priceMinor)
        val synced = Subscriptions.sync(s, listOf(pay("2026-10-21")))
        assertEquals(mine, synced.decor)
    }
}
