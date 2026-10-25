package ru.maxrubtsoff.bankagg.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Payment
import ru.maxrubtsoff.bankagg.domain.model.Subscriptions
import ru.maxrubtsoff.bankagg.domain.model.TourSample

class TourSampleTest {

    private val today = LocalDate.of(2026, 10, 24)

    @Test fun everySectionHasSomething() {
        val d = TourSample.data(today)
        assertTrue(d.banks.isNotEmpty())
        assertTrue(d.accounts.isNotEmpty())
        assertTrue(d.transactions.isNotEmpty())
        assertTrue(d.deposits.isNotEmpty())
        assertTrue(d.fxOps.isNotEmpty())
        assertTrue(d.rates.isNotEmpty())
        assertTrue(d.cash.amount.minor > 0)
        assertTrue(d.cashback.isNotEmpty())
        assertTrue(d.subscriptions.isNotEmpty())
        assertEquals(CashbackMonth.keyOf(today), d.monthKey)
        assertTrue(d.cashback.all { it.monthKey == d.monthKey })
    }

    @Test fun repeatedCallGivesTheSame() {
        assertEquals(TourSample.data(today), TourSample.data(today))
        assertEquals(TourSample.scannedCashbackLines(), TourSample.scannedCashbackLines())
    }

    @Test fun accountsAddUpToTheMockup() {
        // TourA1: 184 320,50 ₽ on three accounts.
        assertEquals(18_432_050L, TourSample.accounts(today).sumOf { it.balance.minor })
        assertEquals("184${Money.NBSP}320,50", Money.ofMinor(18_432_050L).formatNumber())
    }

    @Test fun purchaseTakesTheBalanceDownToTheBanksFigure() {
        val before = TourSample.accounts(today).first { it.id == TourSample.ACCOUNT_SAMPLE }.balance.minor
        assertEquals(TourSample.BALANCE_AFTER_PURCHASE_MINOR, before - TourSample.PURCHASE_MINOR)
        assertEquals(-TourSample.PURCHASE_MINOR, TourSample.purchase(today).amount.minor)
    }

    @Test fun depositsAreActiveToday() {
        TourSample.deposits(today).forEach { assertTrue(it.id, it.endDate.isAfter(today) && it.startDate.isBefore(today)) }
        assertTrue(TourSample.scannedDepositDraft(today).isComplete)
    }

    @Test fun capitalIsCountedWithoutExceptions() {
        val snap = TourSample.capital(today)
        assertEquals(CapitalPart.entries.size, snap.parts.size)
        assertTrue(snap.parts.all { it.hasData && it.valueMinor > 0 })
        assertEquals(snap.parts.sumOf { it.valueMinor }, snap.totalMinor)
        assertTrue(snap.currency.missingRates.isEmpty())
    }

    @Test fun finderSeesOneSubscriptionOnItsOwn() {
        val d = TourSample.data(today)
        val payments = d.transactions.map { t ->
            Payment(t.counterparty!!, -t.amount.minor, Instant.ofEpochMilli(t.postedAt).atZone(ZoneOffset.UTC).toLocalDate(), t.brand)
        }
        val found = Subscriptions.find(payments, Subscriptions.knownPayees(d.subscriptions), today)
        assertEquals(listOf(TourSample.FOUND_PAYEE), found.map { it.payee })
        // The kept one is on the list and its payments agree with it: syncing changes nothing.
        val plus = d.subscriptions.first { it.payee == TourSample.PLUS_PAYEE }
        assertEquals(plus, Subscriptions.sync(plus, payments))
    }
}
