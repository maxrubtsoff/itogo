package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.EmptyStart
import ru.maxrubtsoff.bankagg.domain.model.EmptyStart.Lead
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features

class EmptyStartTest {

    private val accountsAndCashback = Features(setOf(Feature.ACCOUNTS, Feature.CASHBACK))

    @Test fun noBanks_asksToPickThem() {
        assertEquals(Lead.PICK_BANKS, EmptyStart.plan(Features.ALL, collectingBanks = 0, notificationAccess = true).lead)
    }

    @Test fun banksChosen_noAccess_asksForAccess() {
        assertEquals(Lead.GRANT_ACCESS, EmptyStart.plan(Features.ALL, collectingBanks = 9, notificationAccess = false).lead)
    }

    @Test fun banksChosen_withAccess_waits_andNeverAsksToPickBanks() {
        assertEquals(Lead.WAITING, EmptyStart.plan(Features.ALL, collectingBanks = 9, notificationAccess = true).lead)
    }

    @Test fun onlyAccountsAndCashback_offersNoDepositOrCurrency() {
        val p = EmptyStart.plan(accountsAndCashback, collectingBanks = 9, notificationAccess = true)
        assertFalse(p.addDeposit)
        assertFalse(p.addCurrency)
        assertFalse(p.setCash)
    }

    @Test fun manualSections_areOfferedWhenKept() {
        val p = EmptyStart.plan(Features(setOf(Feature.DEPOSITS, Feature.CASH)), collectingBanks = 0, notificationAccess = false)
        assertEquals(Lead.NONE, p.lead)
        assertTrue(p.addDeposit)
        assertFalse(p.addCurrency)
        assertTrue(p.setCash)
    }
}
