package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.security.DbPasswordSeal
import java.io.File

class DbPasswordSealTest {

    private val passphrase = "a1b2c3d4".repeat(8).toByteArray(Charsets.US_ASCII)

    private fun tempFile() = File.createTempFile("db_secret_pw", ".bin").also { it.delete(); it.deleteOnExit() }

    @Test fun round_trip_with_the_right_password() {
        val f = tempFile()
        DbPasswordSeal.write(f, passphrase, "correct horse".toCharArray(), iterations = 1_000)
        assertArrayEquals(passphrase, DbPasswordSeal.open(f, "correct horse".toCharArray()))
    }

    @Test fun wrong_password_opens_nothing() {
        val f = tempFile()
        DbPasswordSeal.write(f, passphrase, "correct horse".toCharArray(), iterations = 1_000)
        assertNull(DbPasswordSeal.open(f, "wrong horse".toCharArray()))
    }

    @Test fun no_file_opens_nothing() {
        assertNull(DbPasswordSeal.open(tempFile(), "x".toCharArray()))
    }

    @Test fun rewriting_replaces_the_seal_and_leaves_no_leftover() {
        val f = tempFile()
        DbPasswordSeal.write(f, passphrase, "first one".toCharArray(), iterations = 1_000)
        DbPasswordSeal.write(f, passphrase, "second one".toCharArray(), iterations = 1_000)
        assertNull(DbPasswordSeal.open(f, "first one".toCharArray()))
        assertArrayEquals(passphrase, DbPasswordSeal.open(f, "second one".toCharArray()))
        assertTrue(f.isFile)
        assertFalse(File(f.path + ".tmp").exists())
    }
}
