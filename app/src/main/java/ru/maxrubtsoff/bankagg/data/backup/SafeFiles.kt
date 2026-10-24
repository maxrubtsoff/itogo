package ru.maxrubtsoff.bankagg.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Paths and keys that come from a backup file are someone else's text (B-30): a
 * crafted copy could point a shot at the database and have it deleted, or name a
 * bank `../../x` to write outside the logo folder. Everything that turns such text
 * into a file goes through here and stays inside the app's own folder.
 */
object SafeFiles {

    /** The most a backup file may hold; anything bigger isn't one of ours. */
    const val MAX_BACKUP_BYTES = 50L * 1024 * 1024

    private val KEY = Regex("^[A-Za-z0-9_:-]{1,128}$")

    /** A bank key that is safe to use in a file name. */
    fun isSafeKey(key: String): Boolean = KEY.matches(key)

    /** [path] if it really lies inside [dir] (after resolving `..` and links), else null. */
    fun inside(dir: File, path: String): File? = runCatching {
        val base = dir.canonicalFile
        val file = File(path).canonicalFile
        file.takeIf { it.path.startsWith(base.path + File.separator) }
    }.getOrNull()

    /** The same file name, in [dir]: a copy from another phone keeps only the name. */
    fun rebase(dir: File, path: String): String {
        val name = File(path).name
        if (name.isEmpty() || name == "." || name == "..") return File(dir, "missing").path
        return File(dir, name).path
    }

    /** Deletes [path] only when it lies inside [dir]. */
    fun deleteInside(dir: File, path: String): Boolean = inside(dir, path)?.takeIf { it.isFile }?.delete() ?: false

    /** Reads the whole stream as UTF-8, refusing anything over [max] bytes. */
    fun readLimited(input: InputStream, max: Long = MAX_BACKUP_BYTES): String =
        String(readLimitedBytes(input, max), Charsets.UTF_8)

    /** Reads the whole stream, refusing anything over [max] bytes. */
    fun readLimitedBytes(input: InputStream, max: Long = MAX_BACKUP_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw TooBig()
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    class TooBig : IOException("too big")
}
