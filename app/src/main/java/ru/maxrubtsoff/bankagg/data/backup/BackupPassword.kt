package ru.maxrubtsoff.bankagg.data.backup

import android.content.Context
import android.util.Base64
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys

/**
 * The password of copies sent elsewhere (F-26): asked once, then kept on the
 * phone sealed with its Keystore key, so «Отправить копию» is one tap after that.
 */
class BackupPassword(context: Context) {

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
    }

    private companion object {
        const val KEY = "password"
    }
}
