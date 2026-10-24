package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import ru.maxrubtsoff.bankagg.domain.model.LockAttempts
import ru.maxrubtsoff.bankagg.domain.model.StoredPin
import ru.maxrubtsoff.bankagg.domain.repository.PinStore
import ru.maxrubtsoff.bankagg.domain.usecase.CheckPin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** B-27: pauses after wrong PINs; B-32: the hash is sealed with a phone-bound key. */
class CheckPinTest {

    private val seal = AppLock.Seal { b -> Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")) }.doFinal(b) }

    private class Store(var pin: StoredPin?) : PinStore {
        var attempts = LockAttempts.NONE
        override suspend fun lockPin() = pin
        override suspend fun setLockPin(pin: StoredPin?) { this.pin = pin }
        override suspend fun lockAttempts() = attempts
        override suspend fun setLockAttempts(attempts: LockAttempts) { this.attempts = attempts }
    }

    @Test fun pauseGrowsAfterFiveWrong() {
        assertEquals(0L, AppLock.pauseMs(4))
        assertEquals(30_000L, AppLock.pauseMs(5))
        assertEquals(60_000L, AppLock.pauseMs(6))
        assertEquals(5 * 60_000L, AppLock.pauseMs(7))
        assertEquals(60 * 60_000L, AppLock.pauseMs(20))
    }

    @Test fun clockMovedBackDoesNotStretchThePause() {
        val a = LockAttempts(5, lastFailAt = 1_000_000)
        assertEquals(30_000L, AppLock.waitLeft(a, now = 0))
        assertEquals(10_000L, AppLock.waitLeft(a, now = 1_020_000))
        assertEquals(0L, AppLock.waitLeft(a, now = 2_000_000))
    }

    @Test fun keyedHashNeedsTheKey() {
        val stored = AppLock.hash("4821", iterations = 1000, seal = seal)
        assertTrue(stored.keyed)
        assertTrue(AppLock.verify("4821", stored, seal))
        assertFalse(AppLock.verify("4821", stored, null))
        assertFalse(AppLock.verify("4821", stored.copy(keyed = false), seal))
    }

    @Test fun fifthWrongPinRests_rightOneDuringRestIsNotChecked() = runTest {
        var now = 1_000_000L
        val store = Store(AppLock.hash("4821", iterations = 1000, seal = seal))
        val check = CheckPin(store, seal) { now }
        repeat(4) { assertEquals(CheckPin.Result.Wrong(null), check("0000")) }
        val fifth = check("0000") as CheckPin.Result.Wrong
        assertEquals(CheckPin.Rest(30_000L, 60_000L), fifth.rest)
        now += 10_000
        assertTrue(check("4821") is CheckPin.Result.Paused)
        now += 25_000
        assertEquals(CheckPin.Result.Ok, check("4821"))
        assertEquals(LockAttempts.NONE, store.attempts)
        assertNull(check.rest())
    }

    @Test fun plainHashIsResealedOnTheFirstRightPin() = runTest {
        val store = Store(AppLock.hash("4821", iterations = 1000))
        val check = CheckPin(store, seal) { 0L }
        assertEquals(CheckPin.Result.Ok, check("4821"))
        assertTrue(store.pin!!.keyed)
        assertEquals(CheckPin.Result.Ok, check("4821"))
    }

    @Test fun newPinStartsTheCountOver() = runTest {
        val store = Store(AppLock.hash("4821", iterations = 1000, seal = seal))
        store.attempts = LockAttempts(7, 0L)
        CheckPin(store, seal) { 0L }.setNew("1111")
        assertEquals(LockAttempts.NONE, store.attempts)
        assertTrue(AppLock.verify("1111", store.pin!!, seal))
    }
}
