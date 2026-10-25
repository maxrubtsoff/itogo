package ru.maxrubtsoff.bankagg.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class, AccountEntity::class, TransactionEntity::class,
        CashbackEntity::class, CashbackShotEntity::class,
        DepositEntity::class, DepositShotEntity::class,
        FxOperationEntity::class, FxRateEntity::class, CashEntity::class, SubscriptionEntity::class,
        DepositTopUpEntity::class
    ],
    version = 15,
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

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // v6 drops the category dictionary for fixed groups (domain CategoryGroups)
        // and adds groupKey / shot drafts. v7 adds the deposits and deposit_shots
        // tables. v8 adds events.contentHash (dedup by content within a time
        // window). v9 adds fx_operations, fx_rates and cash and drops
        // accounts.kind. The app is in development, so older schemas are rebuilt by the
        // destructive fallback rather than migrated. From v10 on, real data lives
        // here, so each new version comes with a migration that keeps it.
        // v11: an account can be left out of the capital (U-18).
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

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `deposit_topups` (`id` TEXT NOT NULL, `depositId` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deposit_topups_depositId` ON `deposit_topups` (`depositId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    val passphrase = DatabaseCipher.prepare(context.applicationContext, NAME)
                    if (passphrase == null) {
                        // An encrypted file whose key is gone can't be opened by anyone: start
                        // empty rather than crash on every launch; the backup brings data back.
                        val file = context.getDatabasePath(NAME)
                        if (file.exists() && !DatabaseCipher.isPlain(file)) context.deleteDatabase(NAME)
                    }
                    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                        .apply { passphrase?.let { openHelperFactory(SupportOpenHelperFactory(it)) } }
                        // Data is rebuilt from notifications and protected by backup,
                        // so a schema bump rebuilds rather than crashing on update.
                        .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { instance = it }
                }
            }

        const val NAME = "bankagg.db"
    }
}
