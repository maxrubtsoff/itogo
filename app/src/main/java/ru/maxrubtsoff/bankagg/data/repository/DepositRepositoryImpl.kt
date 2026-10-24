package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.maxrubtsoff.bankagg.data.local.DepositEntity
import ru.maxrubtsoff.bankagg.data.local.DepositTopUpDao
import ru.maxrubtsoff.bankagg.data.local.DepositTopUpEntity
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate
import ru.maxrubtsoff.bankagg.data.local.DepositDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository

class DepositRepositoryImpl(
    private val dao: DepositDao,
    private val topUps: DepositTopUpDao
) : DepositRepository {

    private fun withTopUps(list: List<DepositEntity>, ups: List<DepositTopUpEntity>): List<Deposit> {
        val byDeposit = ups.groupBy { it.depositId }
        return list.map { e -> e.toDomain().copy(topUps = byDeposit[e.id].orEmpty().map { it.toDomain() }) }
    }

    override fun observeAll(): Flow<List<Deposit>> =
        combine(dao.observeAll(), topUps.observeAll()) { list, ups -> withTopUps(list, ups) }

    override fun observe(id: String): Flow<Deposit?> =
        combine(dao.observe(id), topUps.observeAll()) { e, ups -> e?.let { withTopUps(listOf(it), ups).first() } }

    override suspend fun get(id: String): Deposit? = dao.get(id)?.let { withTopUps(listOf(it), topUps.all()).first() }

    override suspend fun add(deposit: Deposit) = dao.upsert(deposit.toEntity())

    override suspend fun update(deposit: Deposit) = dao.upsert(deposit.toEntity())

    override suspend fun delete(id: String) {
        dao.deleteById(id)
        topUps.deleteForDeposit(id)
    }

    override suspend fun addTopUp(depositId: String, topUp: DepositTopUp) =
        topUps.upsert(DepositTopUpEntity(topUp.id, depositId, topUp.date.toEpochDay(), topUp.amount.minor))

    override suspend fun deleteTopUp(id: String) = topUps.deleteById(id)
}

private fun DepositTopUpEntity.toDomain() = DepositTopUp(id, LocalDate.ofEpochDay(epochDay), Money.ofMinor(amountMinor))
