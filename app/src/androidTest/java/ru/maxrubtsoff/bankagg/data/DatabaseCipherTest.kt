package ru.maxrubtsoff.bankagg.data

import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.maxrubtsoff.bankagg.data.local.DatabaseCipher

/** F-26: a plain database is converted in place, keeping its rows and Room's version. */
@RunWith(AndroidJUnit4::class)
class DatabaseCipherTest {

    @Test fun plainDatabase_becomesEncrypted_withTheSameData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.getDatabasePath("cipher-test.db").apply { parentFile?.mkdirs(); delete() }
        PlainDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, balanceMinor INTEGER)")
            db.execSQL("INSERT INTO accounts VALUES ('alfa:RUB', 8614000)")
            db.version = 11
        }
        assertTrue(DatabaseCipher.isPlain(file))

        System.loadLibrary("sqlcipher")
        val passphrase = "a".repeat(64).toByteArray(Charsets.US_ASCII)
        assertTrue(DatabaseCipher.encryptInPlace(file, passphrase))
        assertFalse(DatabaseCipher.isPlain(file))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("alfa:RUB"))

        val db = SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null)
        try {
            assertEquals(11, db.version)
            db.rawQuery("SELECT balanceMinor FROM accounts WHERE id = 'alfa:RUB'", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(8614000L, c.getLong(0))
            }
        } finally {
            db.close()
            file.delete()
        }
    }
}
