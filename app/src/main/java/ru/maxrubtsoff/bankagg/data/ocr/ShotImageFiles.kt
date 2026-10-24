package ru.maxrubtsoff.bankagg.data.ocr

import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import java.io.File

/**
 * Screenshots waiting to be sorted out are kept encrypted (F-26) with the
 * phone's key; recognised ones are deleted once their data is saved. Older
 * pictures saved before this are plain and are sealed on the next start.
 */
object ShotImageFiles {

    /** The picture's bytes, opened if sealed; null if it is gone or can't be opened. */
    fun read(path: String): ByteArray? = runCatching {
        val bytes = File(path).takeIf { it.isFile }?.readBytes() ?: return null
        when (BackupCipher.kind(bytes)) {
            BackupCipher.Kind.DEVICE -> BackupCipher.open(bytes, PhoneKeys.backupKey() ?: return null)
            else -> bytes
        }
    }.getOrNull()

    /** Sealed with the phone's key; as is where the Keystore doesn't work. */
    fun seal(bytes: ByteArray): ByteArray = PhoneKeys.backupKey()?.let { BackupCipher.seal(bytes, it) } ?: bytes

    /** Seals plain pictures left from before F-26. */
    fun sealExisting(dirs: List<File>) {
        val key = PhoneKeys.backupKey() ?: return
        dirs.flatMap { it.listFiles()?.toList().orEmpty() }.filter { it.isFile }.forEach { f ->
            runCatching {
                val bytes = f.readBytes()
                if (BackupCipher.kind(bytes) == BackupCipher.Kind.DEVICE) return@runCatching
                val tmp = File(f.path + ".tmp")
                tmp.writeBytes(BackupCipher.seal(bytes, key))
                if (!tmp.renameTo(f)) tmp.delete()
            }
        }
    }
}
