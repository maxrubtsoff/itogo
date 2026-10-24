package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored deposit. Amounts are minor units (копейки); [rateBp] is the annual rate
 * ×100. Dates are epoch-days. [exitMinor] is null when the payout isn't known (then
 * it is computed from the rate). [method] is the [ru.maxrubtsoff.bankagg.domain.model.AccrualMethod] name.
 */
@Entity(tableName = "deposits")
data class DepositEntity(
    @PrimaryKey val id: String,
    val brandKey: String,
    val title: String?,
    val principalMinor: Long,
    val rateBp: Int,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val exitMinor: Long?,
    val method: String,
    val createdAt: Long
)
