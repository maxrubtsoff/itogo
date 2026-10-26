package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import ru.maxrubtsoff.bankagg.data.local.AppDatabase
import ru.maxrubtsoff.bankagg.data.local.CashEntity
import ru.maxrubtsoff.bankagg.data.local.FxDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.CashHistory
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import java.time.LocalDate

class FxRepositoryImpl(private val dao: FxDao) : FxRepository {
    override fun observeAll(): Flow<List<FxOperation>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun all(): List<FxOperation> = dao.all().map { it.toDomain() }
    override suspend fun get(id: String): FxOperation? = dao.get(id)?.toDomain()
    override suspend fun upsert(op: FxOperation) = dao.upsert(op.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}

class RatesRepositoryImpl(private val dao: FxDao) : RatesRepository {
    override fun observeAll(): Flow<List<FxRate>> = dao.observeRates().map { l -> l.map { it.toDomain() } }
    override suspend fun all(): List<FxRate> = dao.allRates().map { it.toDomain() }
    override suspend fun save(rates: List<FxRate>) = dao.upsertRates(rates.map { it.toEntity() })
    override suspend fun latestOnOrBefore(currency: String, date: LocalDate): FxRate? =
        dao.latestOnOrBefore(currency, date.toEpochDay())?.toDomain()
}

class CashRepositoryImpl(private val db: AppDatabase) : CashRepository {
    private val dao = db.fxDao()

    override fun observe(): Flow<CashBalance?> = dao.observeCash().map { it?.toDomain() }
    override suspend fun get(): CashBalance? = dao.cash()?.toDomain()
    override fun observeChanges(): Flow<List<CashChange>> = dao.observeCashChanges().map { l -> l.map { it.toDomain() } }
    override suspend fun set(balance: CashBalance) = db.withTransaction {
        val prev = dao.cash()?.toDomain()
        dao.setCash(CashEntity(amountMinor = balance.amount.minor, updatedAt = balance.updatedAt))
        CashHistory.change(prev, balance)?.let { dao.insertCashChange(it.toEntity()) }
        Unit
    }
}
