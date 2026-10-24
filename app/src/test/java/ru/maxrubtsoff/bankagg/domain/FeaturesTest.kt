package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features

class FeaturesTest {

    @Test fun noAccounts_noNotifications_butBanksForDeposits() {
        val f = Features(setOf(Feature.DEPOSITS, Feature.CASHBACK))
        assertFalse(f.collectsNotifications)
        assertTrue(f.needsBanks)
        assertTrue(f.showCapital)
        assertEquals(setOf(CapitalPart.DEPOSITS), f.capitalParts)
    }

    @Test fun onlyCashback_noCapitalTab() {
        val f = Features(setOf(Feature.CASHBACK))
        assertFalse(f.showCapital)
        assertTrue(f.showCashback)
    }

    @Test fun onlyCash_noBanks() {
        assertFalse(Features(setOf(Feature.CASH)).needsBanks)
    }

    @Test fun lastSectionStays() {
        val one = Features(setOf(Feature.CASH))
        assertEquals(one, one.toggle(Feature.CASH))
        assertEquals(Features(setOf(Feature.CASH, Feature.CASHBACK)), one.toggle(Feature.CASHBACK))
    }

    @Test fun storedKeys_roundTrip_unknownMeansAll() {
        val f = Features(setOf(Feature.ACCOUNTS, Feature.CURRENCY))
        assertEquals(f, Features.decode(Features.encode(f)))
        assertEquals(Features.ALL, Features.decode(null))
        assertEquals(Features.ALL, Features.decode(setOf("junk")))
    }

    /** F-30: a choice saved before subscriptions existed keeps them on; switching them off is remembered. */
    @Test fun subscriptions_onForOldChoices_offWhenSwitchedOff() {
        assertTrue(Features.decode(setOf("deposits", "cashback")).showSubscriptions)
        val off = Features(setOf(Feature.DEPOSITS))
        assertEquals(off, Features.decode(Features.encode(off)))
        assertFalse(Features.decode(Features.encode(off)).showSubscriptions)
    }

    @Test fun subscriptions_needNotifications_andThePaymentsTab() {
        val f = Features(setOf(Feature.SUBSCRIPTIONS))
        assertTrue(f.collectsNotifications)
        assertTrue(f.showPayments)
        assertFalse(f.showCashback)
    }
}
