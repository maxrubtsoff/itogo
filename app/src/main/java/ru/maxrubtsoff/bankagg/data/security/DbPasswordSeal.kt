package ru.maxrubtsoff.bankagg.data.security

import android.content.Context
import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import java.io.File

/**
 * A second way to the database (B-91): its passphrase sealed with the backup
 * password the user set, in a file of its own. Where the phone's key is lost,
 * the same password that opens the copies opens the data. It lives in a file, not
 * in the settings, so a damaged settings store does not take it along.
 *
 * Trade-off, by the user's own choice of setting a password: the database is as
 * strong as that password (PBKDF2 inside [BackupCipher]) for whoever holds the files.
 */
object DbPasswordSeal {

    private const val NAME = "db_secret_pw.bin"

    fun file(context: Context): File = File(context.filesDir, NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    fun write(context: Context, passphrase: ByteArray, password: CharArray) = write(file(context), passphrase, password)

    /** The passphrase, or null when the password does not fit (or there is no seal). */
    fun open(context: Context, password: CharArray): ByteArray? = open(file(context), password)

    internal fun write(file: File, passphrase: ByteArray, password: CharArray, iterations: Int = BackupCipher.ITERATIONS) {
        // Beside it first, then moved: a cut-off write must not spoil the seal that is there.
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(BackupCipher.sealWithPassword(passphrase, password, iterations))
        check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) { "seal not saved" }
    }

    internal fun open(file: File, password: CharArray): ByteArray? {
        if (!file.isFile) return null
        return try {
            BackupCipher.openWithPassword(file.readBytes(), password)
        } catch (e: BackupCipher.WrongKey) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
