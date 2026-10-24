package ru.maxrubtsoff.bankagg.data.repository

import androidx.room.withTransaction
import ru.maxrubtsoff.bankagg.data.local.AppDatabase
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.data.local.CashbackDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository

/** [onChanged] runs after every write (the home-screen widget refreshes itself). */
class CashbackRepositoryImpl(
    private val db: AppDatabase,
    private val dao: CashbackDao = db.cashbackDao(),
    private val onChanged: () -> Unit = {}
) : CashbackRepository {

    override fun observeMonth(monthKey: String): Flow<List<CashbackItem>> =
        dao.observeMonth(monthKey).map { list -> list.map { it.toDomain() } }

    override fun observeMonths(): Flow<List<String>> = dao.observeMonths()

    override suspend fun update(item: CashbackItem) = db.withTransaction {
        val target = dao.listBankMonth(item.monthKey, item.brand.key).map { it.toDomain() }
        CashbackMerge.replacedByEdit(item, target).forEach { dao.deleteById(it) }
        dao.upsert(item.toEntity())
    }.also { onChanged() }

    override suspend fun saveBankMonth(
        monthKey: String,
        brand: BankBrand,
        incoming: List<CashbackItem>,
        append: Boolean
    ) = db.withTransaction {
        val result = if (append) {
            CashbackMerge.append(dao.listBankMonth(monthKey, brand.key).map { it.toDomain() }, incoming)
        } else incoming
        dao.deleteBankMonth(monthKey, brand.key)
        dao.upsertAll(result.map { it.toEntity() })
    }.also { onChanged() }

    override suspend fun countForBankMonth(monthKey: String, brand: BankBrand): Int =
        dao.countForBankMonth(monthKey, brand.key)

    override suspend fun listBankMonth(monthKey: String, brand: BankBrand): List<CashbackItem> =
        dao.listBankMonth(monthKey, brand.key).map { it.toDomain() }

    override suspend fun delete(id: String) = dao.deleteById(id).also { onChanged() }
}
