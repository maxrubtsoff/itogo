package ru.maxrubtsoff.bankagg.data.backup

import android.content.Context
import android.util.Base64
import ru.maxrubtsoff.bankagg.data.local.DatabaseCipher
import ru.maxrubtsoff.bankagg.data.security.DbPasswordSeal
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import kotlin.concurrent.thread

/**
 * The password of copies sent elsewhere (F-26): asked once, then kept on the
 * phone sealed with its Keystore key, so «Отправить копию» is one tap after that.
 */
class BackupPassword(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("backup_secret", Context.MODE_PRIVATE)

    fun get(): CharArray? = runCatching {
        val sealed = prefs.getString(KEY, null) ?: return null
        val key = PhoneKeys.backupKey() ?: return null
        String(BackupCipher.open(Base64.decode(sealed, Base64.NO_WRAP), key), Charsets.UTF_8).toCharArray()
    }.getOrNull()

    fun set(password: CharArray) {
        val key = PhoneKeys.backupKey() ?: return
        val sealed = BackupCipher.seal(String(password).toByteArray(Charsets.UTF_8), key)
        prefs.edit().putString(KEY, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
        // B-91: the database's passphrase is also sealed with this password, so it opens the data
        // even if the phone's key is lost. In the background: the slow key derivation is on purpose.
        val chars = password.copyOf()
        thread(name = "db-password-seal") {
            runCatching {
                val passphrase = DatabaseCipher.storedPassphrase(appContext) ?: return@thread
                DbPasswordSeal.write(appContext, passphrase, chars)
            }
            chars.fill(' ')
        }
    }

    private companion object {
        const val KEY = "password"
    }
}
