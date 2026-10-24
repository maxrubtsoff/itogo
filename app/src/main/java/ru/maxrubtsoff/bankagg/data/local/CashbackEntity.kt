package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cashback",
    indices = [Index(value = ["monthKey"]), Index(value = ["monthKey", "brandKey"])]
)
data class CashbackEntity(
    @PrimaryKey val id: String,
    val monthKey: String,
    val brandKey: String,
    val category: String,
    val percent: Double,
    val createdAt: Long,
    val groupKey: String
)
