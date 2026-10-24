package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem

interface CashbackRepository {
    fun observeMonth(monthKey: String): Flow<List<CashbackItem>>
    fun observeMonths(): Flow<List<String>>
    /** Upserts an existing item by id (used when editing). */
    suspend fun update(item: CashbackItem)
    suspend fun countForBankMonth(monthKey: String, brand: BankBrand): Int
    /**
     * Atomically writes [incoming] as [brand]'s cashback for [monthKey]: with
     * [append] merged into what is saved ([ru.maxrubtsoff.bankagg.domain.model.CashbackMerge.append]),
     * otherwise replacing the bank's month.
     */
    suspend fun saveBankMonth(monthKey: String, brand: BankBrand, incoming: List<CashbackItem>, append: Boolean)
    /** A bank's cashback rows for a month, oldest first. */
    suspend fun listBankMonth(monthKey: String, brand: BankBrand): List<CashbackItem>
    suspend fun delete(id: String)
}
