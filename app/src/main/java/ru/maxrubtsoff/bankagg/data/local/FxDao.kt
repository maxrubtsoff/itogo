package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(op: FxOperationEntity)

    @Query("SELECT * FROM fx_operations ORDER BY epochDay, createdAt")
    fun observeAll(): Flow<List<FxOperationEntity>>

    @Query("SELECT * FROM fx_operations ORDER BY epochDay, createdAt")
    suspend fun all(): List<FxOperationEntity>

    @Query("SELECT * FROM fx_operations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): FxOperationEntity?

    @Query("DELETE FROM fx_operations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM fx_operations")
    suspend fun clear()

    // ---- rates ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRates(rates: List<FxRateEntity>)

    @Query("SELECT * FROM fx_rates")
    fun observeRates(): Flow<List<FxRateEntity>>

    @Query("SELECT * FROM fx_rates")
    suspend fun allRates(): List<FxRateEntity>

    @Query("SELECT * FROM fx_rates WHERE code = :code AND epochDay <= :epochDay ORDER BY epochDay DESC LIMIT 1")
    suspend fun latestOnOrBefore(code: String, epochDay: Long): FxRateEntity?

    @Query("DELETE FROM fx_rates")
    suspend fun clearRates()

    // ---- cash ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCash(cash: CashEntity)

    @Query("SELECT * FROM cash WHERE id = 1")
    fun observeCash(): Flow<CashEntity?>

    @Query("SELECT * FROM cash WHERE id = 1")
    suspend fun cash(): CashEntity?

    @Query("DELETE FROM cash")
    suspend fun clearCash()
}
