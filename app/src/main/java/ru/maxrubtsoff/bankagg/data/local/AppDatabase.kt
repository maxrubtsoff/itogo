package ru.maxrubtsoff.bankagg.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.room.RoomDatabase
import ru.maxrubtsoff.bankagg.domain.model.StorageDecision
import java.io.File

@Database(
    entities = [
        EventEntity::class, AccountEntity::class, TransactionEntity::class,
        CashbackEntity::class, CashbackShotEntity::class,
        DepositEntity::class, DepositShotEntity::class,
        FxOperationEntity::class, FxRateEntity::class, CashEntity::class, CashChangeEntity::class, SubscriptionEntity::class,
        DepositTopUpEntity::class, CapitalSnapshotEntity::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun eventDao(): EventDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun cashbackDao(): CashbackDao
    abstract fun cashbackShotDao(): CashbackShotDao
    abstract fun depositDao(): DepositDao
    abstract fun depositShotDao(): DepositShotDao
    abstract fun fxDao(): FxDao
    abstract fun depositTopUpDao(): DepositTopUpDao
    abstract fun capitalSnapshotDao(): CapitalSnapshotDao

    companion object {

        /** B-95: the day of month a subscription is paid on; old rows have none (taken from the date). */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN anchorDay INTEGER")
            }
        }

        /** B-92: the notification key keeps two equal pushes without a balance apart; old rows have none. */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN notificationKey TEXT")
            }
        }

        /** F-48: the log of cash hand edits. Starts empty — the past deltas are unknown, none are invented. */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`atMs` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `deltaMinor` INTEGER NOT NULL)"
                )
            }
        }

        /** U-66: deposits keep no screens; the ones tied to a deposit go (the image files are cleaned apart). */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM deposit_shots WHERE depositId IS NOT NULL")
            }
        }

        /**
         * F-36: the capital's daily points. The table starts empty; the past is
         * rebuilt once in the background ([ru.maxrubtsoff.bankagg.domain.usecase.BuildCapitalHistory]),
         * as it needs the CBR rates, which a migration must not wait for.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `capital_days` (`date` INTEGER NOT NULL, `totalMinor` INTEGER NOT NULL, " +
                        "`accountsMinor` INTEGER NOT NULL, `depositsMinor` INTEGER NOT NULL, `fxMinor` INTEGER NOT NULL, " +
                        "`cashMinor` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** B-64: a saved deposit keeps all its screenshots — one nullable column. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deposit_shots` ADD COLUMN `depositId` TEXT")
            }
        }

        /** F-33: a subscription's own look — four nullable columns, nothing is lost. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `colorArgb` INTEGER")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `letters` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `textColorArgb` INTEGER")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `logoFile` TEXT")
            }
        }

        /** F-27: subscriptions. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subscriptions` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `priceMinor` INTEGER NOT NULL, " +
                        "`periodUnit` TEXT NOT NULL, `periodDays` INTEGER NOT NULL, `nextEpochDay` INTEGER NOT NULL, `brandKey` TEXT, " +
                        "`payee` TEXT, `remind` INTEGER NOT NULL, `status` TEXT NOT NULL, `previousPriceMinor` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** B-29: a counted balance for banks without one in their pushes. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `checkedAt` INTEGER")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `estimated` INTEGER NOT NULL DEFAULT 0")
                // A balance the bank already told is exact as of its last update.
                db.execSQL("UPDATE `accounts` SET `checkedAt` = `updatedAt` WHERE `balanceMinor` != 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v6 drops the category dictionary for fixed groups (domain CategoryGroups)
        // and adds groupKey / shot drafts. v7 adds the deposits and deposit_shots
        // tables. v8 adds events.contentHash (dedup by content within a time
        // window). v9 adds fx_operations, fx_rates and cash and drops
        // accounts.kind. The app is in development, so older schemas are rebuilt by the
        // destructive fallback rather than migrated. From v10 on, real data lives
        // here, so each new version comes with a migration that keeps it.
        // v11: an account can be left out of the capital (U-18).
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `deposit_topups` (`id` TEXT NOT NULL, `depositId` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deposit_topups_depositId` ON `deposit_topups` (`depositId`)")
            }
        }

        /** What [open] came to (B-91). */
        sealed interface OpenResult {
            class Opened(val db: AppDatabase) : OpenResult
            /** The file is left exactly as it is; [decision] says why it was not opened. */
            class Blocked(val decision: StorageDecision) : OpenResult
        }

        /**
         * Opens the database, or says why not. A file that can't be opened is never
         * deleted and no empty one is made in its place: the user decides on the
         * last-resort screen ([quarantine] moves the file aside, it is not erased).
         */
        fun open(context: Context): OpenResult {
            val app = context.applicationContext
            val prepared = DatabaseCipher.prepare(app, NAME)
            if (prepared is DatabaseCipher.Prepared.Blocked) return OpenResult.Blocked(prepared.decision)
            val passphrase = (prepared as? DatabaseCipher.Prepared.Ready)?.passphrase
            val db = Room.databaseBuilder(app, AppDatabase::class.java, NAME)
                .apply { passphrase?.let { openHelperFactory(SupportOpenHelperFactory(it)) } }
                // Data is rebuilt from notifications and protected by backup,
                // so a schema bump rebuilds rather than crashing on update.
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .fallbackToDestructiveMigration()
                .build()
            // Opened now, not at the first query: a file that does not open must be known here.
            return try {
                db.openHelper.writableDatabase
                OpenResult.Opened(db)
            } catch (e: Exception) {
                android.util.Log.w("AppDatabase", "database did not open", e)
                runCatching { db.close() }
                OpenResult.Blocked(StorageDecision.CORRUPT)
            }
        }

        /**
         * «Начать заново»: the old file (and its journal files) is renamed to
         * `bankagg.db.broken`, never deleted. False if it could not be moved.
         */
        fun quarantine(context: Context): Boolean {
            val file = context.getDatabasePath(NAME)
            if (!file.exists()) return true
            var target = File(file.path + ".broken")
            if (target.exists()) target = File(file.path + ".broken." + System.currentTimeMillis())
            if (!file.renameTo(target)) return false
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                val side = File(file.path + suffix)
                if (side.exists()) side.renameTo(File(target.path + suffix))
            }
            return true
        }

        const val NAME = "bankagg.db"
    }
}
