package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPartsToggle
import ru.maxrubtsoff.bankagg.domain.model.FlipDetector
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask

/** App lock (F-10), hidden amounts (F-11), capital parts toggle (F-12). */
class PrivacyTest {

    private val nb = Money.NBSP

    // ---- PIN ----

    @Test fun pin_hashedAndVerified() {
        val stored = AppLock.hash("4821", iterations = 1000)
        assertFalse(stored.hash.contains("4821"))
        assertTrue(AppLock.verify("4821", stored))
        assertFalse(AppLock.verify("4822", stored))
        assertFalse(AppLock.verify("48", stored))
    }

    @Test fun pin_saltDiffers() {
        val a = AppLock.hash("1111", iterations = 1000)
        val b = AppLock.hash("1111", iterations = 1000)
        assertTrue(a.hash != b.hash)
    }

    @Test fun pin_onlyFourDigits() {
        assertTrue(AppLock.isValidPin("0007"))
        assertFalse(AppLock.isValidPin("123"))
        assertFalse(AppLock.isValidPin("12a4"))
    }

    @Test fun lock_onColdStartAndAfterTenMinutes() {
        val t = 1_000_000L
        assertTrue(AppLock.needsUnlock(enabled = true, coldStart = true, backgroundedAt = null, now = t))
        assertFalse(AppLock.needsUnlock(enabled = false, coldStart = true, backgroundedAt = null, now = t))
        assertFalse(AppLock.needsUnlock(true, false, backgroundedAt = t - 5 * 60_000, now = t))
        assertTrue(AppLock.needsUnlock(true, false, backgroundedAt = t - 11 * 60_000, now = t))
        assertFalse(AppLock.needsUnlock(true, false, backgroundedAt = null, now = t))
    }

    // ---- money mask ----

    @Test fun mask_findsAmounts() {
        val parts = MoneyMask.split("+18${nb}083,24${nb}₽ · получено за 57 дней")
        assertEquals(listOf(true, false), parts.map { it.money })
        assertEquals("+18${nb}083,24${nb}₽", parts.first().text)
    }

    @Test fun mask_leavesPercentsDaysAndCounts() {
        assertFalse(MoneyMask.hasMoney("16% · 40 дн."))
        assertFalse(MoneyMask.hasMoney("3 вклада"))
        assertFalse(MoneyMask.hasMoney("сегодня"))
    }

    @Test fun mask_compactAndForeign() {
        assertTrue(MoneyMask.hasMoney("2,80${nb}млн"))
        assertTrue(MoneyMask.hasMoney("1${nb}500${nb}$"))
        assertTrue(MoneyMask.hasMoney("2${nb}000 USD"))
        val parts = MoneyMask.split("накоплено 18${nb}083 из 20${nb}938,49${nb}₽")
        assertEquals(listOf(false, true), parts.map { it.money })
    }

    // ---- cold start (B-19) ----

    @Test fun closedFromRecents_asksAgain() {
        // The process survived the swipe, but the screen is new: PIN.
        val cold = AppLock.isColdStart(freshScreen = true, unlockedInProcess = true)
        assertTrue(AppLock.needsUnlock(true, cold, backgroundedAt = null, now = 0))
    }

    @Test fun rotation_doesNotAsk() {
        val cold = AppLock.isColdStart(freshScreen = false, unlockedInProcess = true)
        assertFalse(AppLock.needsUnlock(true, cold, backgroundedAt = null, now = 0))
    }

    @Test fun shortBreak_doesNotAsk_longOneDoes() {
        val min = 60_000L
        assertFalse(AppLock.needsUnlock(true, coldStart = false, backgroundedAt = 0, now = 5 * min))
        assertTrue(AppLock.needsUnlock(true, coldStart = false, backgroundedAt = 0, now = 11 * min))
    }

    // ---- flip ----

    @Test fun flip_firesAlmostAtOnce() {
        val f = FlipDetector()
        assertFalse(f.onGravity(9.8f, 0))
        assertFalse(f.onGravity(-9.7f, 100))
        assertFalse(f.onGravity(-9.7f, 200))
        assertTrue(f.onGravity(-9.7f, 260))       // ~150 ms face down is enough
        assertFalse(f.onGravity(-9.7f, 900))      // once per flip
    }

    @Test fun flip_everyFlipFiresAgain() {
        // The caller toggles on each fire: hide, then show on the next flip.
        val f = FlipDetector()
        assertFalse(f.onGravity(-9.8f, 0))
        assertTrue(f.onGravity(-9.8f, 200))
        assertFalse(f.onGravity(9.8f, 500))
        assertFalse(f.onGravity(-9.8f, 800))
        assertTrue(f.onGravity(-9.8f, 1000))
    }

    @Test fun flip_wobbleDoesNothing() {
        val f = FlipDetector()
        assertFalse(f.onGravity(-9.0f, 0))
        assertFalse(f.onGravity(2.0f, 80))
        assertFalse(f.onGravity(-9.0f, 120))
        assertFalse(f.onGravity(2.0f, 200))
        assertFalse(f.onGravity(-7.0f, 400))      // tilted, not face down
        assertFalse(f.onGravity(-7.0f, 900))
    }

    // ---- capital parts ----

    @Test fun parts_lastOneStays() {
        val all = CapitalPart.entries.toSet()
        assertEquals(all - CapitalPart.CASH, CapitalPartsToggle.toggle(all, CapitalPart.CASH))
        assertEquals(all, CapitalPartsToggle.toggle(all - CapitalPart.CASH, CapitalPart.CASH))
        assertEquals(setOf(CapitalPart.DEPOSITS), CapitalPartsToggle.toggle(setOf(CapitalPart.DEPOSITS), CapitalPart.DEPOSITS))
    }
}
