package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A top-up of a deposit (F-20). */
@Entity(tableName = "deposit_topups", indices = [Index(value = ["depositId"])])
data class DepositTopUpEntity(
    @PrimaryKey val id: String,
    val depositId: String,
    val epochDay: Long,
    val amountMinor: Long
)

@Dao
interface DepositTopUpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topUp: DepositTopUpEntity)

    @Query("SELECT * FROM deposit_topups ORDER BY epochDay")
    fun observeAll(): Flow<List<DepositTopUpEntity>>

    @Query("SELECT * FROM deposit_topups")
    suspend fun all(): List<DepositTopUpEntity>

    @Query("DELETE FROM deposit_topups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM deposit_topups WHERE depositId = :depositId")
    suspend fun deleteForDeposit(depositId: String)

    @Query("DELETE FROM deposit_topups")
    suspend fun clear()
}
