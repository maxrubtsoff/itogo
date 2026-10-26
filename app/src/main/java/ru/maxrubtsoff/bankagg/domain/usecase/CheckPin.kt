package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import ru.maxrubtsoff.bankagg.domain.model.LockAttempts
import ru.maxrubtsoff.bankagg.domain.repository.PinStore

/**
 * The PIN on the lock screen (B-27, B-32): pauses after too many wrong tries,
 * and an old plain hash is re-sealed with the phone's key on the first right one.
 */
class CheckPin(
    private val store: PinStore,
    private val seal: AppLock.Seal?,
    private val now: () -> Long = System::currentTimeMillis,
    /** Monotonic clock (B-100); -1 when there is none. */
    private val tick: () -> Long = { -1L }
) {
    /** The keypad rests [leftMs] more; the next mistake after it costs [nextMs]. */
    data class Rest(val leftMs: Long, val nextMs: Long)

    sealed interface Result {
        data object Ok : Result
        /** Wrong; [rest] — that was one too many, the keypad rests. */
        data class Wrong(val rest: Rest?) : Result
        /** Still resting from earlier mistakes; the PIN wasn't checked. */
        data class Paused(val rest: Rest) : Result
    }

    suspend operator fun invoke(pin: String): Result {
        val attempts = store.lockAttempts()
        rest(attempts)?.let { return Result.Paused(it) }
        val stored = store.lockPin() ?: return Result.Ok
        val right = withContext(Dispatchers.Default) { AppLock.verify(pin, stored, seal) }
        if (right) {
            store.setLockAttempts(LockAttempts.NONE)
            if (!stored.keyed && seal != null) store.setLockPin(withContext(Dispatchers.Default) { AppLock.hash(pin, seal = seal) })
            return Result.Ok
        }
        val next = LockAttempts(attempts.failures + 1, now(), tick())
        store.setLockAttempts(next)
        return Result.Wrong(rest(next))
    }

    /** The current rest, if any (the screen opens mid-pause after a restart). */
    suspend fun rest(): Rest? = rest(store.lockAttempts())

    private fun rest(a: LockAttempts): Rest? {
        val left = AppLock.waitLeft(a, now(), tick())
        return if (left > 0) Rest(left, AppLock.pauseMs(a.failures + 1)) else null
    }

    /** The owner proved it otherwise (fingerprint, the phone's own lock): the count starts over. */
    suspend fun forgive() = store.setLockAttempts(LockAttempts.NONE)

    /** A new PIN, sealed with the phone's key when there is one. */
    suspend fun setNew(pin: String) {
        store.setLockPin(withContext(Dispatchers.Default) { AppLock.hash(pin, seal = seal) })
        store.setLockAttempts(LockAttempts.NONE)
    }
}
