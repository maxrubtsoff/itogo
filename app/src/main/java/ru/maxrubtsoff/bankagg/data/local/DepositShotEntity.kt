package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An uploaded deposit screenshot. [brandKey] is empty until the bank is known;
 * [status] is the [ru.maxrubtsoff.bankagg.domain.model.ShotStatus] name;
 * [draftJson] holds the recognised deposit fields (see DepositShotMappers).
 */
@Entity(tableName = "deposit_shots")
data class DepositShotEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val brandKey: String,
    val status: String,
    val createdAt: Long,
    val fileName: String?,
    val draftJson: String,
    val depositId: String? = null
)
