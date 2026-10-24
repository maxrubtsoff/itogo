package ru.maxrubtsoff.bankagg.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup files are encrypted (F-26), AES-256-GCM, in one of two ways:
 * - [Kind.DEVICE] — with a key in this phone's Keystore: the copies the app keeps
 *   by itself; nobody types anything, and nobody else (nor this phone after a
 *   reset) can open them;
 * - [Kind.PASSWORD] — with the user's backup password: the copy sent elsewhere,
 *   to restore on another phone or after a reinstall.
 * Layout: `ITOGO` 1 · kind 1 · [salt 16 · iterations 4] · iv 12 · ciphertext+tag.
 * Old copies are plain JSON ([Kind.PLAIN]).
 */
object BackupCipher {

    enum class Kind { PLAIN, DEVICE, PASSWORD, UNKNOWN }

    class WrongKey : Exception("wrong key")

    private val MAGIC = byteArrayOf('I'.code.toByte(), 'T'.code.toByte(), 'O'.code.toByte(), 'G'.code.toByte(), 'O'.code.toByte(), 1)
    private const val DEVICE: Byte = 1
    private const val PASSWORD: Byte = 2
    private const val IV = 12
    private const val SALT = 16
    private const val TAG_BITS = 128
    /** Slow on purpose: a password is the only thing between the file and a stranger. */
    const val ITERATIONS = 310_000

    fun kind(bytes: ByteArray): Kind = when {
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) -> when (bytes[MAGIC.size]) {
            DEVICE -> Kind.DEVICE
            PASSWORD -> Kind.PASSWORD
            else -> Kind.UNKNOWN
        }
        bytes.firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte() -> Kind.PLAIN
        else -> Kind.UNKNOWN
    }

    /** With the phone's key. A Keystore key picks its own IV; it is stored in front. */
    fun seal(plain: ByteArray, key: SecretKey): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = c.iv
        require(iv.size == IV) { "unexpected IV" }
        return MAGIC + DEVICE + iv + c.doFinal(plain)
    }

    fun open(bytes: ByteArray, key: SecretKey): ByteArray {
        require(kind(bytes) == Kind.DEVICE) { "not a device copy" }
        val at = MAGIC.size + 1
        return decrypt(key, bytes.copyOfRange(at, at + IV), bytes.copyOfRange(at + IV, bytes.size))
    }

    fun sealWithPassword(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val salt = ByteArray(SALT).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt, iterations)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return MAGIC + PASSWORD + salt + ByteBuffer.allocate(4).putInt(iterations).array() + iv + c.doFinal(plain)
    }

    fun openWithPassword(bytes: ByteArray, password: CharArray): ByteArray {
        require(kind(bytes) == Kind.PASSWORD) { "not a password copy" }
        var at = MAGIC.size + 1
        val salt = bytes.copyOfRange(at, at + SALT); at += SALT
        val iterations = ByteBuffer.wrap(bytes, at, 4).int; at += 4
        require(iterations in 1..10_000_000) { "bad header" }
        val iv = bytes.copyOfRange(at, at + IV); at += IV
        return decrypt(derive(password, salt, iterations), iv, bytes.copyOfRange(at, bytes.size))
    }

    private fun decrypt(key: SecretKey, iv: ByteArray, data: ByteArray): ByteArray = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }.doFinal(data)
    } catch (e: AEADBadTagException) {
        throw WrongKey()
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, iterations, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private operator fun ByteArray.plus(b: Byte): ByteArray = this + byteArrayOf(b)
}
