package ru.maxrubtsoff.bankagg.data.backup

import android.content.Context
import android.net.Uri
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys

/**
 * Whether a picked copy opens at all, before anything is changed (B-91): only
 * the file and the password are checked, the database is not touched.
 */
class BackupFileCheck(private val context: Context) {

    enum class Result { OPENS, NEEDS_PASSWORD, WRONG_PASSWORD, OTHER_PHONE, NOT_OURS, UNREADABLE }

    fun check(uri: Uri, password: CharArray?): Result {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { SafeFiles.readLimitedBytes(it) }
        }.getOrElse { return if (it is SafeFiles.TooBig) Result.NOT_OURS else Result.UNREADABLE } ?: return Result.UNREADABLE
        return when (BackupCipher.kind(bytes)) {
            BackupCipher.Kind.PLAIN -> Result.OPENS
            BackupCipher.Kind.DEVICE -> {
                val key = (PhoneKeys.lookup() as? PhoneKeys.KeyLookup.Found)?.key ?: return Result.OTHER_PHONE
                try {
                    BackupCipher.open(bytes, key)
                    Result.OPENS
                } catch (e: BackupCipher.WrongKey) {
                    Result.OTHER_PHONE
                } catch (e: Exception) {
                    Result.OTHER_PHONE
                }
            }
            BackupCipher.Kind.PASSWORD -> {
                if (password == null) return Result.NEEDS_PASSWORD
                try {
                    BackupCipher.openWithPassword(bytes, password)
                    Result.OPENS
                } catch (e: BackupCipher.WrongKey) {
                    Result.WRONG_PASSWORD
                } catch (e: Exception) {
                    Result.UNREADABLE
                }
            }
            BackupCipher.Kind.UNKNOWN -> Result.NOT_OURS
        }
    }
}
