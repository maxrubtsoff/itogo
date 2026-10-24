package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashbackShotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shot: CashbackShotEntity)

    @Query("SELECT * FROM cashback_shots WHERE monthKey = :monthKey ORDER BY createdAt DESC")
    fun observeMonth(monthKey: String): Flow<List<CashbackShotEntity>>

    @Query("SELECT * FROM cashback_shots WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CashbackShotEntity?

    @Query("SELECT * FROM cashback_shots WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<CashbackShotEntity?>

    @Query("UPDATE cashback_shots SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM cashback_shots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM cashback_shots")
    suspend fun all(): List<CashbackShotEntity>

    @Query("DELETE FROM cashback_shots")
    suspend fun clear()
}
