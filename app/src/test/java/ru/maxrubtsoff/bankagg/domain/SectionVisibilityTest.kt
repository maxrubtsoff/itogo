package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AppTab
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.ReminderKind
import ru.maxrubtsoff.bankagg.domain.model.ReminderKinds

/**
 * What is shown follows what the user keeps in «Что храним»: the «Обзор» tab
 * (U-50), the capture log (U-51) and the kinds of reminder (U-52).
 */
class SectionVisibilityTest {

    @Test fun onlySubscriptions_noOverview_appOpensOnPayments() {
        val f = Features(setOf(Feature.SUBSCRIPTIONS))
        assertFalse(f.showOverview)
        assertEquals(listOf(AppTab.PAYMENTS, AppTab.SETTINGS), f.tabs)
        assertEquals(AppTab.PAYMENTS, f.firstTab)
    }

    @Test fun cashAlone_bringsTheOverviewBack() {
        val f = Features(setOf(Feature.SUBSCRIPTIONS, Feature.CASH))
        assertTrue(f.showOverview)
        assertEquals(listOf(AppTab.OVERVIEW, AppTab.CAPITAL, AppTab.PAYMENTS, AppTab.SETTINGS), f.tabs)
        assertEquals(AppTab.OVERVIEW, f.firstTab)
    }

    @Test fun cashbackAlone_hasAnOverview() {
        val f = Features(setOf(Feature.CASHBACK))
        assertTrue(f.showOverview)
        assertEquals(listOf(AppTab.OVERVIEW, AppTab.PAYMENTS, AppTab.SETTINGS), f.tabs)
    }

    @Test fun settingsTab_isAlwaysThere() {
        assertTrue(AppTab.SETTINGS in Features(setOf(Feature.CASH)).tabs)
    }

    @Test fun captureLog_belongsToAccounts() {
        assertTrue(Features(setOf(Feature.ACCOUNTS)).showCaptureLog)
        // Subscriptions are found in notifications too, but the log is about accounts.
        assertFalse(Features(setOf(Feature.SUBSCRIPTIONS)).showCaptureLog)
        assertTrue(Features(setOf(Feature.SUBSCRIPTIONS)).collectsNotifications)
    }

    @Test fun reminderKinds_followTheKeptSections() {
        assertEquals(
            listOf(ReminderKind.NEW_BANK, ReminderKind.SUBSCRIPTION),
            ReminderKinds.of(Features(setOf(Feature.SUBSCRIPTIONS)))
        )
        assertEquals(
            listOf(ReminderKind.DEPOSIT_ENDING),
            ReminderKinds.of(Features(setOf(Feature.DEPOSITS)))
        )
        assertEquals(
            listOf(ReminderKind.CASHBACK_MONTH),
            ReminderKinds.of(Features(setOf(Feature.CASHBACK)))
        )
    }

    @Test fun reminderSubtitle_namesWhatIsSent() {
        assertEquals("Вклады", ReminderKinds.subtitle(Features(setOf(Feature.DEPOSITS))))
        assertEquals(
            "Новые банки, вклады, кэшбэк, подписки",
            ReminderKinds.subtitle(Features.ALL)
        )
        assertEquals("Выключены", ReminderKinds.subtitle(Features(setOf(Feature.CASH))))
    }
}
