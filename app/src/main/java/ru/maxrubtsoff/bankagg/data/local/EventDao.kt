package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    /** Returns -1 when ignored as a duplicate (unique dedupHash). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: EventEntity): Long

    @Query(
        "SELECT COUNT(*) FROM events WHERE contentHash = :contentHash AND postedAt BETWEEN :from AND :to " +
            "AND (:key IS NULL OR notificationKey IS NULL OR notificationKey = :key)"
    )
    suspend fun countSimilar(contentHash: String, from: Long, to: Long, key: String?): Int

    @Query("SELECT * FROM events ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY postedAt DESC")
    suspend fun all(): List<EventEntity>

    @Query("DELETE FROM events WHERE (parsed = 1 AND postedAt < :parsedBefore) OR (parsed = 0 AND postedAt < :unparsedBefore)")
    suspend fun trim(parsedBefore: Long, unparsedBefore: Long): Int

    @Query("UPDATE events SET parsed = :parsed, dedupHash = :dedupHash, contentHash = :contentHash WHERE id = :id")
    suspend fun updateParse(id: Long, parsed: Boolean, dedupHash: String, contentHash: String)

    @Query("DELETE FROM events")
    suspend fun clear()
}
