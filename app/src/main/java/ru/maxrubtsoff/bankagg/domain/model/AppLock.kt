package ru.maxrubtsoff.bankagg.domain.model

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A PIN as stored: never the digits, only a salted PBKDF2 hash. [keyed]: the hash
 * is also sealed with a key that never leaves the phone (B-32), so a copied
 * settings file can't be brute-forced elsewhere.
 */
data class StoredPin(val hash: String, val salt: String, val iterations: Int, val keyed: Boolean = false)

/**
 * Wrong PINs in a row and when the last one was typed (B-27). [lastFailTick] is
 * the phone's monotonic clock then (-1: unknown): unlike the wall clock it can't
 * be moved by hand, so a clock set forward doesn't skip the pause (B-100).
 */
data class LockAttempts(val failures: Int, val lastFailAt: Long, val lastFailTick: Long = -1L) {
    companion object {
        val NONE = LockAttempts(0, 0L)
    }
}

/**
 * The app lock (F-10): a 4-digit PIN kept as a salted hash, asked on start and
 * after a long stay in the background. After [FREE_TRIES] wrong PINs in a row
 * the keypad pauses, longer each time (B-27).
 */
object AppLock {

    const val PIN_LENGTH = 4

    /** In the background longer than this → ask again on return. */
    const val BACKGROUND_TIMEOUT_MS = 10 * 60 * 1000L

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /** [seal] — the phone-bound key (HMAC); without it the hash is plain PBKDF2. */
    fun hash(pin: String, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS, seal: Seal? = null): StoredPin {
        require(isValidPin(pin)) { "PIN must be $PIN_LENGTH digits" }
        val derived = derive(pin, salt, iterations)
        return StoredPin(hex(seal?.invoke(derived) ?: derived), hex(salt), iterations, keyed = seal != null)
    }

    fun verify(pin: String, stored: StoredPin, seal: Seal? = null): Boolean {
        if (!isValidPin(pin)) return false
        val derived = derive(pin, unhex(stored.salt), stored.iterations)
        val actual = if (stored.keyed) seal?.invoke(derived) ?: return false else derived
        return MessageDigest.isEqual(actual, unhex(stored.hash))
    }

    // ---- wrong PINs (B-27) ----

    /** Wrong PINs allowed in a row before the first pause. */
    const val FREE_TRIES = 5

    /** How long the keypad rests after [failures] wrong PINs in a row. */
    fun pauseMs(failures: Int): Long = when {
        failures < FREE_TRIES -> 0L
        failures == FREE_TRIES -> 30_000L
        failures == FREE_TRIES + 1 -> 60_000L
        failures == FREE_TRIES + 2 -> 5 * 60_000L
        failures == FREE_TRIES + 3 -> 15 * 60_000L
        else -> 60 * 60_000L
    }

    /**
     * Time left before the next try. A clock moved back can't stretch the pause
     * past its length, nor a clock moved forward skip it: within one boot the
     * monotonic [tick] decides; after a reboot (tick restarted) only the wall
     * clock is left, and there a skip is possible.
     */
    fun waitLeft(attempts: LockAttempts, now: Long, tick: Long = -1L): Long {
        val pause = pauseMs(attempts.failures)
        if (pause == 0L) return 0L
        if (attempts.lastFailTick >= 0 && tick >= attempts.lastFailTick) {
            return (pause - (tick - attempts.lastFailTick)).coerceIn(0L, pause)
        }
        return (attempts.lastFailAt + pause - now).coerceIn(0L, pause)
    }

    /**
     * Ask for the PIN now? On a cold start always; on a return only after
     * [BACKGROUND_TIMEOUT_MS] away. [backgroundedAt] is null when the app hasn't
     * been in the background since it was last unlocked.
     */
    /**
     * A cold start asks for the PIN: a fresh screen (the app was closed from
     * recents or by the system, even if its process lived on — B-19) or a
     * process that was never unlocked. A screen recreated on rotation is not one.
     */
    fun isColdStart(freshScreen: Boolean, unlockedInProcess: Boolean): Boolean = freshScreen || !unlockedInProcess

    fun needsUnlock(enabled: Boolean, coldStart: Boolean, backgroundedAt: Long?, now: Long): Boolean = when {
        !enabled -> false
        coldStart -> true
        backgroundedAt == null -> false
        else -> now - backgroundedAt >= BACKGROUND_TIMEOUT_MS
    }

    /** A phone-bound one-way function over the derived bytes (an HMAC with a Keystore key). */
    fun interface Seal {
        operator fun invoke(bytes: ByteArray): ByteArray
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS))
            .encoded

    private fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
