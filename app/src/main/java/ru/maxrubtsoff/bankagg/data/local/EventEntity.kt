package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Raw captured notification, kept verbatim for the diagnostics log and re-parsing. */
@Entity(
    tableName = "events",
    indices = [Index(value = ["dedupHash"], unique = true), Index(value = ["contentHash", "postedAt"])]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val brandKey: String,
    val title: String?,
    val text: String,
    val postedAt: Long,
    val dedupHash: String,
    val parsed: Boolean,
    val contentHash: String = dedupHash,
    val notificationKey: String? = null
)
