package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An uploaded cashback screenshot. [brandKey] is empty until the bank is known.
 * [status] is the [ru.maxrubtsoff.bankagg.domain.model.ShotStatus] name.
 */
@Entity(
    tableName = "cashback_shots",
    indices = [Index(value = ["monthKey"])]
)
data class CashbackShotEntity(
    @PrimaryKey val id: String,
    val monthKey: String,
    val imagePath: String,
    val brandKey: String,   // "" when unknown
    val status: String,
    val createdAt: Long,
    val fileName: String?,
    val linesJson: String   // draft rows, see CashbackShotMappers
)
