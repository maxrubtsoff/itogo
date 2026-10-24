package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CashbackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CashbackEntity>)

    @Query("SELECT * FROM cashback WHERE monthKey = :monthKey ORDER BY percent DESC")
    fun observeMonth(monthKey: String): Flow<List<CashbackEntity>>

    @Query("SELECT DISTINCT monthKey FROM cashback ORDER BY monthKey DESC")
    fun observeMonths(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM cashback WHERE monthKey = :monthKey AND brandKey = :brandKey")
    suspend fun countForBankMonth(monthKey: String, brandKey: String): Int

    @Query("SELECT * FROM cashback WHERE monthKey = :monthKey AND brandKey = :brandKey ORDER BY createdAt")
    suspend fun listBankMonth(monthKey: String, brandKey: String): List<CashbackEntity>

    @Query("DELETE FROM cashback WHERE monthKey = :monthKey AND brandKey = :brandKey")
    suspend fun deleteBankMonth(monthKey: String, brandKey: String)

    @Query("DELETE FROM cashback WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM cashback")
    suspend fun all(): List<CashbackEntity>

    @Query("DELETE FROM cashback")
    suspend fun clear()
}
