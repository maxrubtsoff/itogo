package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A purchase or sale of cash currency. Amount in hundredths, rubles in kopecks, date as epoch day. */
@Entity(tableName = "fx_operations", indices = [Index(value = ["currency"])])
data class FxOperationEntity(
    @PrimaryKey val id: String,
    val currency: String,
    val kind: String,                    // BUY | SELL
    val epochDay: Long,
    val amountMinor: Long,
    val rubMinor: Long,
    val createdAt: Long
)

/** A cached CBR rate: rubles per unit ×1 000 000. */
@Entity(tableName = "fx_rates", primaryKeys = ["code", "epochDay"])
data class FxRateEntity(
    val code: String,
    val epochDay: Long,
    val rubPerUnitMicro: Long
)

/** Cash rubles at home — a single row with id 1. */
@Entity(tableName = "cash")
data class CashEntity(
    @PrimaryKey val id: Int = 1,
    val amountMinor: Long,
    val updatedAt: Long
)

/** F-48: the log of cash hand edits; [deltaMinor] is how much the amount moved. */
@Entity(tableName = "cash_changes")
data class CashChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val amountMinor: Long,
    val deltaMinor: Long
)
