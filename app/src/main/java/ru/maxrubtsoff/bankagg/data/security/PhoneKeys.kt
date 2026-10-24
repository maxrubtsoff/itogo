package ru.maxrubtsoff.bankagg.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Keys that live in the Android Keystore: they never leave the phone, not even
 * to the app. Copying the app's files elsewhere gets nothing that opens them.
 */
object PhoneKeys {

    private const val STORE = "AndroidKeyStore"
    private const val PIN = "itogo_pin_v1"
    private const val BACKUP = "itogo_backup_v1"

    /** The PIN hash seal (B-32); null where the Keystore doesn't work — the PIN still does. */
    fun pinSeal(): AppLock.Seal? = runCatching {
        val key = hmacKey(PIN)
        // Fail now rather than on the lock screen.
        Mac.getInstance("HmacSHA256").init(key)
        AppLock.Seal { bytes -> Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(bytes) }
    }.getOrNull()

    /** The key the app's own backup copies are sealed with (F-26); null where the Keystore doesn't work. */
    fun backupKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(BACKUP, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply {
            init(
                KeyGenParameterSpec.Builder(BACKUP, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }.getOrNull()

    /** Before the app's data is wiped: nothing sealed with these keys opens again. */
    fun deleteAll() {
        runCatching {
            val ks = KeyStore.getInstance(STORE).apply { load(null) }
            ks.aliases().toList().filter { it.startsWith("itogo_") }.forEach { ks.deleteEntry(it) }
        }
    }

    private fun hmacKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, STORE).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()
    }
}
