package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DepositDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deposit: DepositEntity)

    @Query("SELECT * FROM deposits ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DepositEntity>>

    @Query("SELECT * FROM deposits WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DepositEntity?

    @Query("SELECT * FROM deposits WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<DepositEntity?>

    @Query("DELETE FROM deposits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM deposits")
    suspend fun all(): List<DepositEntity>

    @Query("DELETE FROM deposits")
    suspend fun clear()
}
