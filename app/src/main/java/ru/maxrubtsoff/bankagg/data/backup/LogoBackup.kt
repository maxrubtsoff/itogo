package ru.maxrubtsoff.bankagg.data.backup

import java.io.File
import java.util.Base64

/**
 * Own bank pictures inside the backup (F-25): the cut-out PNG (a few dozen KB)
 * travels as base64 next to its bank and is written back on restore. A broken
 * picture is dropped — the bank comes back with its letters.
 */
object LogoBackup {

    private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /** The picture at [path] as base64, or null if there is none. */
    fun encode(path: String?): String? {
        val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
        return runCatching { Base64.getEncoder().encodeToString(file.readBytes()) }.getOrNull()
    }

    /** The PNG bytes, or null when the text isn't a PNG in base64. */
    fun decode(text: String?): ByteArray? {
        if (text.isNullOrBlank()) return null
        val bytes = runCatching { Base64.getDecoder().decode(text) }.getOrNull() ?: return null
        return bytes.takeIf { it.size > PNG.size && it.copyOfRange(0, PNG.size).contentEquals(PNG) }
    }

    /** Writes the picture for [bankKey] into [dir] (old ones of that bank go) and returns its path. */
    fun write(dir: File, bankKey: String, bytes: ByteArray): String {
        require(SafeFiles.isSafeKey(bankKey)) { "unsafe bank key" }
        dir.mkdirs()
        val prefix = bankKey.replace(':', '_') + "_"
        dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
        val file = File(dir, prefix + System.currentTimeMillis() + ".png")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
