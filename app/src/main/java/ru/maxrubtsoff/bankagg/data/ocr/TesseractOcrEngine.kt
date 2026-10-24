package ru.maxrubtsoff.bankagg.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine
import java.io.File

/**
 * On-device OCR via Tesseract4Android — offline and free, and (unlike ML Kit) it
 * recognises Cyrillic, which is why the project uses it.
 *
 * The Russian model (`rus.traineddata`) ships in assets and is copied once into
 * the app's files dir on first use (Tesseract reads from a `tessdata/` folder on
 * disk). If the model is absent — e.g. a build without it bundled — [isReady]
 * returns false and recognition is skipped, so the app still runs and the user
 * falls back to manual entry. Nothing here touches the network.
 */
class TesseractOcrEngine(
    private val context: Context,
    private val language: String = "rus"
) : OcrEngine {

    private val dataDir: File by lazy { File(context.filesDir, "tess").apply { mkdirs() } }
    private val tessdataDir: File by lazy { File(dataDir, "tessdata").apply { mkdirs() } }
    private val modelFile: File by lazy { File(tessdataDir, "$language.traineddata") }

    // Tesseract's native API isn't reentrant; serialise access. The same lock
    // guards copying the model, so two first calls can't write it at once.
    private val lock = Mutex()

    /** Initialised once and reused: loading the model takes far longer than one page. */
    private var api: TessBaseAPI? = null

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        lock.withLock { ensureModel() }
    }

    override suspend fun recognize(imagePath: String): String? = read(imagePath, listOf(1f))

    // Half size catches banner figures Tesseract skips when glyphs are huge;
    // double size catches the fine print of a document photographed whole.
    override suspend fun recognizeThorough(imagePath: String): String? = read(imagePath, listOf(1f, 0.5f, 2f))

    private suspend fun read(imagePath: String, scales: List<Float>): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!ensureModel()) return@withLock null
            val bitmap = decode(imagePath) ?: return@withLock null
            try {
                val engine = api ?: TessBaseAPI().takeIf { it.init(dataDir.absolutePath, language) }
                    ?.also { api = it }
                    ?: return@withLock null
                scales.mapNotNull { s ->
                    val scaled = scaled(bitmap, s) ?: return@mapNotNull null
                    try {
                        engine.setImage(scaled)
                        engine.getUTF8Text()?.trim()?.takeIf { it.isNotBlank() }
                    } finally {
                        runCatching { engine.clear() }
                        if (scaled !== bitmap) runCatching { scaled.recycle() }
                    }
                }.joinToString("\n\n").takeIf { it.isNotBlank() }
            } catch (t: Throwable) {
                // A broken native state is not reused.
                runCatching { api?.recycle() }
                api = null
                null
            } finally {
                runCatching { bitmap.recycle() }
            }
        }
    }

    /** [bitmap] at [scale]; null when the result would be too small or too large to be useful. */
    private fun scaled(bitmap: Bitmap, scale: Float): Bitmap? {
        if (scale == 1f) return bitmap
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        if (minOf(w, h) < 300 || maxOf(w, h) > 4200) return null
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
    private fun ensureModel(): Boolean {
        val sizeFile = File(tessdataDir, "$language.size")
        val expected = sizeFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
        if (expected != null && modelFile.exists() && modelFile.length() == expected) return true
        return runCatching {
            val tmp = File(tessdataDir, "$language.traineddata.tmp")
            val copied = context.assets.open("tessdata/$language.traineddata").use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied <= 0L || tmp.length() != copied) return@runCatching false
            modelFile.delete()
            if (!tmp.renameTo(modelFile)) return@runCatching false
            sizeFile.writeText(copied.toString())
            modelFile.length() == copied
        }.getOrDefault(false)
    }

    /** Decode a file to a bitmap, downscaled and EXIF-rotated for reliable OCR. */
    private fun decode(path: String): Bitmap? {
        // The stored picture is sealed (F-26): opened in memory, never written back plain.
        val bytes = ShotImageFiles.read(path) ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Keep the long side around 2000px — enough for text, not wasteful.
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 2000) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return applyExifRotation(bytes, bitmap)
    }

    private fun applyExifRotation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream())
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }
}
