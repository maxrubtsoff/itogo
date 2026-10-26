package ru.maxrubtsoff.bankagg.data.local

import android.content.Context
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import ru.maxrubtsoff.bankagg.domain.model.DbFile
import ru.maxrubtsoff.bankagg.domain.model.KeyStatus
import ru.maxrubtsoff.bankagg.domain.model.SecretCheck
import ru.maxrubtsoff.bankagg.domain.model.StorageDecision
import ru.maxrubtsoff.bankagg.domain.model.StorageGuard
import ru.maxrubtsoff.bankagg.domain.model.StorageState
import java.io.File
import java.security.SecureRandom

/**
 * The database file is encrypted (F-26) with SQLCipher. Its passphrase is
 * random, made once, and kept sealed with the phone's Keystore key — the file
 * copied off the phone (root, a backup tool) is unreadable.
 *
 * An existing plain database is converted once, in place; if anything about
 * that fails the app keeps the plain one rather than losing data.
 */
object DatabaseCipher {

    private const val PREFS = "db_secret"
    private const val KEY = "passphrase"
    private val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** What [prepare] came to. */
    sealed interface Prepared {
        /** Open the encrypted file with this passphrase. */
        class Ready(val passphrase: ByteArray) : Prepared
        /** Keep (or start) a plain database: nothing to lose, the Keystore doesn't work here. */
        data object Plain : Prepared
        /** Do not touch the file (B-91): the policy says wait, ask for the password or give up. */
        class Blocked(val decision: StorageDecision) : Prepared
    }

    /**
     * Decides by [StorageGuard] what to do with the file and does only that. A new
     * key or passphrase appears only for [StorageDecision.CREATE_NEW] and
     * [StorageDecision.CONVERT_PLAIN]; nothing here deletes a database.
     */
    fun prepare(context: Context, name: String): Prepared {
        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lookup = PhoneKeys.lookup()
        val (check, opened) = checkSecret(prefs.getString(KEY, null), lookup)
        val state = StorageState(
            file = when {
                !file.exists() || file.length() == 0L -> DbFile.ABSENT
                isPlain(file) -> DbFile.PLAIN
                else -> DbFile.ENCRYPTED
            },
            key = when (lookup) {
                is PhoneKeys.KeyLookup.Found -> KeyStatus.PRESENT
                PhoneKeys.KeyLookup.Absent -> KeyStatus.ABSENT
                PhoneKeys.KeyLookup.Failed -> KeyStatus.UNAVAILABLE
            },
            secret = check,
            passwordSealStored = passwordSealStored(context)
        )
        val decision = StorageGuard.decide(state)
        PhoneKeys.creationAllowed = StorageGuard.mayCreateKeyOrSecret(decision)
        return when (decision) {
            StorageDecision.OPEN -> Prepared.Ready(opened ?: return Prepared.Blocked(StorageDecision.WAIT))
            StorageDecision.CREATE_NEW, StorageDecision.CONVERT_PLAIN -> {
                val key = (lookup as? PhoneKeys.KeyLookup.Found)?.key ?: PhoneKeys.create() ?: return Prepared.Plain
                val passphrase = (if (decision == StorageDecision.CONVERT_PLAIN) opened else null) ?: fresh()
                // commit, not apply: the passphrase must be on disk before the database is converted with it.
                val stored = runCatching {
                    prefs.edit().putString(KEY, Base64.encodeToString(BackupCipher.seal(passphrase, key), Base64.NO_WRAP)).commit()
                }.getOrDefault(false)
                when {
                    !stored -> Prepared.Plain
                    decision == StorageDecision.CONVERT_PLAIN && !encryptInPlace(file, passphrase) -> Prepared.Plain
                    else -> Prepared.Ready(passphrase)
                }
            }
            StorageDecision.PLAIN_FALLBACK -> Prepared.Plain
            else -> Prepared.Blocked(decision)
        }
    }

    /** A plain SQLite file (not yet converted). */
    fun isPlain(file: File): Boolean {
        if (!file.isFile || file.length() < PLAIN_HEADER.size) return false
        val head = ByteArray(PLAIN_HEADER.size)
        file.inputStream().use { it.read(head) }
        return head.contentEquals(PLAIN_HEADER)
    }

    /**
     * Copies a plain database into an encrypted one next to it and swaps them.
     * Uses SQLCipher's own export, so tables, indices and Room's version move as they are.
     */
    fun encryptInPlace(file: File, passphrase: ByteArray): Boolean {
        val tmp = File(file.path + "-enc")
        return runCatching {
            tmp.delete()
            // CREATE: an attached database opens with the main one's flags, and the new file doesn't exist yet.
            val plain = SQLiteDatabase.openDatabase(
                file.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null
            )
            try {
                // Room's file is in WAL mode: one connection only, so ATTACH and the export share it.
                plain.disableWriteAheadLogging()
                val version = plain.version
                plain.execSQL("ATTACH DATABASE '${tmp.path.replace("'", "''")}' AS encrypted KEY '${String(passphrase, Charsets.US_ASCII)}'")
                // A SELECT runs only when stepped, through a cursor; inside a transaction it stays
                // on the connection that has `encrypted` attached, not on a pooled reader.
                plain.beginTransaction()
                try {
                    plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                    plain.setTransactionSuccessful()
                } finally {
                    plain.endTransaction()
                }
                plain.execSQL("DETACH DATABASE encrypted")
                // The export leaves Room's schema version behind; it is set on the new file itself
                // (a PRAGMA on the attached one may land on another pooled connection).
                val enc = SQLiteDatabase.openDatabase(tmp.path, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null)
                try {
                    enc.version = version
                } finally {
                    enc.close()
                }
            } finally {
                plain.close()
            }
            listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            check(file.delete() && tmp.renameTo(file)) { "swap failed" }
        }.onFailure { android.util.Log.w("DatabaseCipher", "encrypt failed", it); tmp.delete() }.isSuccess
    }

    /** Whether the stored passphrase opens with the key, and the passphrase if it does. */
    private fun checkSecret(stored: String?, lookup: PhoneKeys.KeyLookup): Pair<SecretCheck, ByteArray?> {
        if (stored == null) return SecretCheck.NOT_STORED to null
        return when (lookup) {
            PhoneKeys.KeyLookup.Absent -> SecretCheck.REJECTED to null
            PhoneKeys.KeyLookup.Failed -> SecretCheck.UNKNOWN to null
            is PhoneKeys.KeyLookup.Found -> try {
                SecretCheck.OPENS to BackupCipher.open(Base64.decode(stored, Base64.NO_WRAP), lookup.key)
            } catch (e: BackupCipher.WrongKey) {
                SecretCheck.REJECTED to null
            } catch (e: IllegalArgumentException) {
                SecretCheck.REJECTED to null   // the stored text is not a sealed passphrase at all
            } catch (e: IndexOutOfBoundsException) {
                SecretCheck.REJECTED to null
            } catch (e: Exception) {
                android.util.Log.w("DatabaseCipher", "opening the passphrase failed", e)
                SecretCheck.UNKNOWN to null    // not a clear «no»: wait rather than call it a loss
            }
        }
    }

    /** Whether the passphrase is also sealed with the backup password. */
    private fun passwordSealStored(context: Context): Boolean = false

    /**
     * 64 hex characters: plain ASCII, so the same bytes work for Room and for
     * the `KEY '…'` of the one-time conversion.
     */
    private fun fresh(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
}
