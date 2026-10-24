package ru.maxrubtsoff.bankagg.data.local

import android.content.Context
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
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

    /** The passphrase for Room, or null — then the database stays plain (the Keystore doesn't work here). */
    fun prepare(context: Context, name: String): ByteArray? {
        val passphrase = passphrase(context) ?: return null
        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name)
        if (isPlain(file) && !encryptInPlace(file, passphrase)) return null
        return passphrase
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

    /**
     * 64 hex characters: plain ASCII, so the same bytes work for Room and for
     * the `KEY '…'` of the one-time conversion.
     */
    private fun passphrase(context: Context): ByteArray? = runCatching {
        val key = PhoneKeys.backupKey() ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return BackupCipher.open(Base64.decode(it, Base64.NO_WRAP), key) }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
        // commit, not apply: the key must be on disk before the database is converted with it.
        check(prefs.edit().putString(KEY, Base64.encodeToString(BackupCipher.seal(fresh, key), Base64.NO_WRAP)).commit())
        fresh
    }.getOrNull()
}
