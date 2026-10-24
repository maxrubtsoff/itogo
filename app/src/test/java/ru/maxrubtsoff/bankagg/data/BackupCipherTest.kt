package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import javax.crypto.KeyGenerator

/** F-26: copies are encrypted with the phone's key or a password; old ones are plain JSON. */
class BackupCipherTest {

    private val json = """{"version":7,"accounts":[{"balanceMinor":4821000}]}""".toByteArray()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun deviceCopy_roundTrip_andUnreadable() {
        val sealed = BackupCipher.seal(json, key)
        assertEquals(BackupCipher.Kind.DEVICE, BackupCipher.kind(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("balanceMinor"))
        assertArrayEquals(json, BackupCipher.open(sealed, key))
    }

    @Test(expected = BackupCipher.WrongKey::class)
    fun deviceCopy_otherPhone() {
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        BackupCipher.open(BackupCipher.seal(json, key), other)
    }

    @Test fun passwordCopy_roundTrip() {
        val sealed = BackupCipher.sealWithPassword(json, "correct horse".toCharArray(), iterations = 1000)
        assertEquals(BackupCipher.Kind.PASSWORD, BackupCipher.kind(sealed))
        assertArrayEquals(json, BackupCipher.openWithPassword(sealed, "correct horse".toCharArray()))
    }

    @Test(expected = BackupCipher.WrongKey::class)
    fun passwordCopy_wrongPassword() {
        val sealed = BackupCipher.sealWithPassword(json, "correct horse".toCharArray(), iterations = 1000)
        BackupCipher.openWithPassword(sealed, "wrong".toCharArray())
    }

    @Test(expected = BackupCipher.WrongKey::class)
    fun damagedCopy() {
        val sealed = BackupCipher.seal(json, key)
        sealed[sealed.size - 3] = (sealed[sealed.size - 3] + 1).toByte()
        BackupCipher.open(sealed, key)
    }

    @Test fun kinds() {
        assertEquals(BackupCipher.Kind.PLAIN, BackupCipher.kind("  {\"version\":1}".toByteArray()))
        assertEquals(BackupCipher.Kind.UNKNOWN, BackupCipher.kind("PK\u0003\u0004".toByteArray()))
        assertEquals(BackupCipher.Kind.UNKNOWN, BackupCipher.kind(ByteArray(0)))
    }
}
