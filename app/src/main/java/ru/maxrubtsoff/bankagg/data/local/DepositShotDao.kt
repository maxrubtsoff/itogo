package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DepositShotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shot: DepositShotEntity)

    @Query("SELECT * FROM deposit_shots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DepositShotEntity>>

    @Query("SELECT * FROM deposit_shots WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DepositShotEntity?

    @Query("SELECT * FROM deposit_shots WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<DepositShotEntity?>

    @Query("UPDATE deposit_shots SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM deposit_shots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM deposit_shots")
    suspend fun all(): List<DepositShotEntity>

    @Query("DELETE FROM deposit_shots")
    suspend fun clear()
}
