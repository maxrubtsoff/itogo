package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.di.LockSession
import ru.maxrubtsoff.bankagg.domain.model.AppLock

class LockSessionTest {

    @Test fun new_session_is_not_phone_verified() {
        assertFalse(LockSession().phoneVerified)
    }

    @Test fun markUnlocked_clears_phoneVerified() {
        val s = LockSession().apply { phoneVerified = true }
        s.markUnlocked()
        assertFalse(s.phoneVerified)
    }

    @Test fun markUnlocked_sets_unlocked_and_clears_background() {
        val s = LockSession().apply { backgroundedAt = 5L }
        s.markUnlocked()
        assertTrue(s.unlocked)
        assertNull(s.backgroundedAt)
    }

    @Test fun after_enabling_pin_return_does_not_lock() {
        val s = LockSession()
        s.markUnlocked()
        assertFalse(AppLock.needsUnlock(true, !s.unlocked, s.backgroundedAt, 1_000L))
    }

    @Test fun background_longer_than_timeout_still_locks() {
        val s = LockSession()
        s.markUnlocked()
        s.backgroundedAt = 0L
        assertTrue(AppLock.needsUnlock(true, !s.unlocked, s.backgroundedAt, AppLock.BACKGROUND_TIMEOUT_MS + 1))
    }
}
