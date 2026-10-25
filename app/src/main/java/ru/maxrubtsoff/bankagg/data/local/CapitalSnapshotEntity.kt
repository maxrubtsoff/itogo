package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One day of the capital history (F-36): the total and each part in kopecks, the day as epoch day. */
@Entity(tableName = "capital_days")
data class CapitalSnapshotEntity(
    @PrimaryKey val date: Long,
    val totalMinor: Long,
    val accountsMinor: Long,
    val depositsMinor: Long,
    val fxMinor: Long,
    val cashMinor: Long
)

@Dao
interface CapitalSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: CapitalSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<CapitalSnapshotEntity>)

    @Query("SELECT * FROM capital_days ORDER BY date")
    fun observeAll(): Flow<List<CapitalSnapshotEntity>>

    @Query("SELECT * FROM capital_days ORDER BY date")
    suspend fun all(): List<CapitalSnapshotEntity>

    @Query("SELECT * FROM capital_days WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: Long, to: Long): List<CapitalSnapshotEntity>

    @Query("DELETE FROM capital_days")
    suspend fun clear()
}

internal fun CapitalSnapshotEntity.toDomain() = ru.maxrubtsoff.bankagg.domain.model.CapitalPoint(
    java.time.LocalDate.ofEpochDay(date), totalMinor,
    mapOf(
        ru.maxrubtsoff.bankagg.domain.model.CapitalPart.ACCOUNTS to accountsMinor,
        ru.maxrubtsoff.bankagg.domain.model.CapitalPart.DEPOSITS to depositsMinor,
        ru.maxrubtsoff.bankagg.domain.model.CapitalPart.CURRENCY to fxMinor,
        ru.maxrubtsoff.bankagg.domain.model.CapitalPart.CASH to cashMinor
    )
)

internal fun ru.maxrubtsoff.bankagg.domain.model.CapitalPoint.toEntity() = CapitalSnapshotEntity(
    date.toEpochDay(), totalMinor,
    valueOf(ru.maxrubtsoff.bankagg.domain.model.CapitalPart.ACCOUNTS),
    valueOf(ru.maxrubtsoff.bankagg.domain.model.CapitalPart.DEPOSITS),
    valueOf(ru.maxrubtsoff.bankagg.domain.model.CapitalPart.CURRENCY),
    valueOf(ru.maxrubtsoff.bankagg.domain.model.CapitalPart.CASH)
)
