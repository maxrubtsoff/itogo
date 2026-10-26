package ru.maxrubtsoff.bankagg.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import java.security.KeyStore
import java.security.UnrecoverableKeyException
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

    /** What asking the Keystore for the backup key came to (B-91): three outcomes, not a null. */
    sealed interface KeyLookup {
        class Found(val key: SecretKey) : KeyLookup
        /** No such key — or one the system says is gone for good. */
        data object Absent : KeyLookup
        /** The Keystore failed to answer: the key may well still be there. */
        data object Failed : KeyLookup
    }

    /**
     * Whether a missing key may be made anew. Off until the storage policy
     * ([ru.maxrubtsoff.bankagg.domain.model.StorageGuard]) says there is nothing to lose:
     * otherwise any caller could replace the key an encrypted database waits for.
     */
    @Volatile var creationAllowed: Boolean = false

    /** Looks the key up; never makes one. */
    fun lookup(): KeyLookup = try {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        val key = ks.getKey(BACKUP, null) as? SecretKey
        if (key != null) KeyLookup.Found(key) else KeyLookup.Absent
    } catch (e: UnrecoverableKeyException) {
        KeyLookup.Absent
    } catch (e: KeyPermanentlyInvalidatedException) {
        KeyLookup.Absent
    } catch (e: Exception) {
        android.util.Log.w("PhoneKeys", "keystore lookup failed", e)
        KeyLookup.Failed
    }

    /** Makes the key. Only after the policy allowed it. */
    fun create(): SecretKey? = runCatching {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply {
            init(
                KeyGenParameterSpec.Builder(BACKUP, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }.getOrNull()

    /** The key the app's own backup copies are sealed with (F-26); null where the Keystore doesn't work or the key is not to be remade. */
    fun backupKey(): SecretKey? = when (val found = lookup()) {
        is KeyLookup.Found -> found.key
        KeyLookup.Absent -> if (creationAllowed) create() else null
        KeyLookup.Failed -> null
    }

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
