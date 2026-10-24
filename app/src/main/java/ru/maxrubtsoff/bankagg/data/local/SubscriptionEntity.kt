package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.PeriodUnit
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.SubscriptionStatus
import java.time.LocalDate

/** A subscription (F-27); an IGNORED row only remembers «Не подписка» for its payee. */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val priceMinor: Long,
    val periodUnit: String,
    val periodDays: Int,
    val nextEpochDay: Long,
    val brandKey: String?,
    val payee: String?,
    val remind: Boolean,
    val status: String,
    val previousPriceMinor: Long?,
    val createdAt: Long
)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY nextEpochDay")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun all(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    title = title,
    priceMinor = priceMinor,
    period = Period(runCatching { PeriodUnit.valueOf(periodUnit) }.getOrDefault(PeriodUnit.MONTH), periodDays),
    nextDate = LocalDate.ofEpochDay(nextEpochDay),
    brand = brandKey?.let(BankBrand::fromKey),
    payee = payee,
    remind = remind,
    status = runCatching { SubscriptionStatus.valueOf(status) }.getOrDefault(SubscriptionStatus.ACTIVE),
    previousPriceMinor = previousPriceMinor,
    createdAt = createdAt
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id, title = title, priceMinor = priceMinor, periodUnit = period.unit.name, periodDays = period.days,
    nextEpochDay = nextDate.toEpochDay(), brandKey = brand?.key, payee = payee, remind = remind,
    status = status.name, previousPriceMinor = previousPriceMinor, createdAt = createdAt
)
