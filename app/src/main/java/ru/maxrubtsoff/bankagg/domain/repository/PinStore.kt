package ru.maxrubtsoff.bankagg.domain.repository

import ru.maxrubtsoff.bankagg.domain.model.LockAttempts
import ru.maxrubtsoff.bankagg.domain.model.StoredPin

/** Where the app lock keeps its PIN hash and the count of wrong tries (B-27). */
interface PinStore {
    /** The PIN (null — lock off). */
    suspend fun lockPin(): StoredPin?
    suspend fun setLockPin(pin: StoredPin?)

    /** Kept on the phone, so a restart doesn't reset the pause. */
    suspend fun lockAttempts(): LockAttempts
    suspend fun setLockAttempts(attempts: LockAttempts)
}
