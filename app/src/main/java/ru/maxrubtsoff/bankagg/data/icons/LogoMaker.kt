package ru.maxrubtsoff.bankagg.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.model.LogoBackground
import java.io.File

/**
 * A bank's own logo from the gallery (F-22): the middle square, scaled to
 * [SIZE] px, with a plain background cut away ([LogoBackground]); saved as a PNG
 * in the app's files. Nothing leaves the phone.
 */
class LogoMaker(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "bank-logos")

    /** The saved file's path, or null if the picture can't be read. */
    suspend fun make(uri: Uri, bankKey: String): String? = withContext(Dispatchers.IO) {
        // A 50 MP photo is decoded already shrunk: the logo is 192 px anyway (U-34).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@withContext null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(SIZE * SIZE)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val last = SIZE - 1
        LogoBackground.detect(listOf(px[0], px[last], px[last * SIZE], px[last * SIZE + last]))?.let { bg ->
            scaled.setPixels(LogoBackground.clear(px, bg), 0, SIZE, 0, 0, SIZE, SIZE)
        }
        dir.mkdirs()
        // A new name each time, so screens holding the old picture redraw.
        val prefix = bankKey.replace(':', '_') + "_"
        dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
        val file = File(dir, prefix + System.currentTimeMillis() + ".png")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.absolutePath
    }

    private companion object {
        const val SIZE = 192
    }
}
