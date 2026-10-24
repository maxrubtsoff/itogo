package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.maxrubtsoff.bankagg.data.backup.SafeFiles
import java.io.File

class SafeFilesTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun keys() {
        assertTrue(SafeFiles.isSafeKey("tbank"))
        assertTrue(SafeFiles.isSafeKey("user:3f2a-11"))
        assertFalse(SafeFiles.isSafeKey("../../databases/x"))
        assertFalse(SafeFiles.isSafeKey("a/b"))
        assertFalse(SafeFiles.isSafeKey(""))
    }

    @Test fun insideRejectsEscapes() {
        val dir = tmp.newFolder("shots")
        val db = tmp.newFile("bankagg.db")
        val shot = File(dir, "shot_1.jpg").apply { writeText("x") }
        assertNotNull(SafeFiles.inside(dir, shot.path))
        assertNull(SafeFiles.inside(dir, db.path))
        assertNull(SafeFiles.inside(dir, File(dir, "../bankagg.db").path))
        assertNull(SafeFiles.inside(dir, dir.path))
    }

    @Test fun deleteOnlyInside() {
        val dir = tmp.newFolder("shots")
        val db = tmp.newFile("bankagg.db")
        assertFalse(SafeFiles.deleteInside(dir, db.path))
        assertTrue(db.exists())
        val shot = File(dir, "shot_1.jpg").apply { writeText("x") }
        assertTrue(SafeFiles.deleteInside(dir, shot.path))
        assertFalse(shot.exists())
    }

    @Test fun rebaseKeepsOnlyTheName() {
        val dir = File("/own/shots")
        assertEquals(File(dir, "shot_1.jpg").path, SafeFiles.rebase(dir, "/data/user/0/other/files/cashback_shots/shot_1.jpg"))
        assertEquals(File(dir, "bankagg.db").path, SafeFiles.rebase(dir, "../../databases/bankagg.db"))
        assertEquals(File(dir, "missing").path, SafeFiles.rebase(dir, ".."))
    }

    @Test fun readLimited() {
        assertEquals("{}", SafeFiles.readLimited("{}".byteInputStream(), max = 10))
        try {
            SafeFiles.readLimited(ByteArray(11).inputStream(), max = 10)
            throw AssertionError("expected TooBig")
        } catch (_: SafeFiles.TooBig) {
        }
    }
}
