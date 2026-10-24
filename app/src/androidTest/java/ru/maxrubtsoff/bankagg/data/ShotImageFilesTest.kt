package ru.maxrubtsoff.bankagg.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageFiles
import java.io.ByteArrayOutputStream
import java.io.File

/** F-26: a stored screenshot is sealed with the phone's key and still decodes for the screen and OCR. */
@RunWith(AndroidJUnit4::class)
class ShotImageFilesTest {

    private fun png(): ByteArray {
        val bmp = Bitmap.createBitmap(40, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFD7A74A.toInt()) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun sealedPicture_readsBackAndDecodes() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "shot-test").apply { mkdirs() }
        val file = File(dir, "shot_1.jpg")
        val plain = png()
        file.writeBytes(ShotImageFiles.seal(plain))
        assertEquals(BackupCipher.Kind.DEVICE, BackupCipher.kind(file.readBytes()))
        val back = ShotImageFiles.read(file.path)!!
        assertArrayEquals(plain, back)
        assertEquals(40, BitmapFactory.decodeByteArray(back, 0, back.size).width)
        dir.deleteRecursively()
    }

    @Test fun oldPlainPictures_getSealed() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "shot-test2").apply { mkdirs() }
        val file = File(dir, "shot_old.jpg").apply { writeBytes(png()) }
        ShotImageFiles.sealExisting(listOf(dir))
        assertFalse(BackupCipher.kind(file.readBytes()) == BackupCipher.Kind.UNKNOWN)
        assertEquals(BackupCipher.Kind.DEVICE, BackupCipher.kind(file.readBytes()))
        assertEquals(40, ShotImageFiles.read(file.path)!!.let { BitmapFactory.decodeByteArray(it, 0, it.size).width })
        dir.deleteRecursively()
    }
}
