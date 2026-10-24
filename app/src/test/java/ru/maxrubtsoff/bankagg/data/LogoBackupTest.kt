package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.maxrubtsoff.bankagg.data.backup.LogoBackup
import java.io.File

class LogoBackupTest {

    @get:Rule val tmp = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3, 4, 5)

    @Test fun pictureSurvivesCopyAndRestore_byteForByte() {
        val original = tmp.newFile("user_1_1.png").apply { writeBytes(png) }
        val text = LogoBackup.encode(original.absolutePath)
        val bytes = LogoBackup.decode(text)!!
        val dir = File(tmp.root, "restored")
        val path = LogoBackup.write(dir, "user:1", bytes)
        assertArrayEquals(png, File(path).readBytes())
        assertTrue(File(path).name.startsWith("user_1_"))
    }

    @Test fun restoreReplacesTheBanksOldPicture() {
        val dir = tmp.newFolder("logos")
        File(dir, "user_1_100.png").writeBytes(png)
        File(dir, "user_2_100.png").writeBytes(png)
        LogoBackup.write(dir, "user:1", png)
        assertEquals(1, dir.listFiles { f -> f.name.startsWith("user_1_") }!!.size)
        assertEquals(1, dir.listFiles { f -> f.name.startsWith("user_2_") }!!.size)
    }

    @Test fun noPicture_orBrokenOne_givesNothing() {
        assertNull(LogoBackup.encode(null))
        assertNull(LogoBackup.encode(File(tmp.root, "missing.png").absolutePath))
        assertNull(LogoBackup.decode(null))
        assertNull(LogoBackup.decode("не base64 !!!"))
        assertNull(LogoBackup.decode(java.util.Base64.getEncoder().encodeToString("plain text".toByteArray())))
    }
}
