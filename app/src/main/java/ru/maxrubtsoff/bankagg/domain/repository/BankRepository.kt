package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks

/** The user's banks (F-22): nothing preinstalled — they add the ones they use. */
interface BankRepository {
    /** The user's banks, in the order they were added. */
    val myBanks: Flow<List<BankBrand>>
    suspend fun current(): List<BankBrand>

    /** Adds a bank (known, from the CBR list or the user's own); adding twice changes nothing. */
    suspend fun add(bank: BankBrand)

    /** Saves changes to a bank already added (own letters, colours, picture, linked app). */
    suspend fun update(bank: BankBrand)

    suspend fun remove(key: String)

    /** The bundled CBR directory. */
    suspend fun cbrEntries(): List<CbrBanks.Entry>
}
