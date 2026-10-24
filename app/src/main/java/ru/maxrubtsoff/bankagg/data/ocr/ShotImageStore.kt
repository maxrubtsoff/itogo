package ru.maxrubtsoff.bankagg.data.ocr

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.data.backup.SafeFiles
import java.io.File
import java.util.UUID

/** A picked screenshot copied into app storage, plus its original file name (if the picker gave one). */
data class SavedImage(val path: String, val displayName: String?)

/**
 * Saves picked cashback screenshots into private app storage and returns their
 * file path. Images never leave the device (fully local app); copying the picked
 * [Uri] here also means the stored shot keeps working after the original is gone.
 * The original display name is kept because some launchers put the bank's app
 * name in it (see ScreenshotFileName).
 */
class ShotImageStore(
    private val context: Context,
    private val dirName: String = "cashback_shots"
) {

    private val dir: File by lazy { File(context.filesDir, dirName).apply { mkdirs() } }

    /** Copy [uri] into app storage; returns the saved file and original name, or null. */
    suspend fun save(uri: Uri): SavedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, "shot_${UUID.randomUUID()}.jpg")
            // Stored sealed with the phone's key (F-26); a screenshot is never 30 MB.
            val bytes = context.contentResolver.openInputStream(uri)?.use { SafeFiles.readLimitedBytes(it, MAX_BYTES) }
                ?: return@runCatching null
            file.writeBytes(ShotImageFiles.seal(bytes))
            SavedImage(file.absolutePath, displayName(uri))
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 30L * 1024 * 1024
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
