package ru.maxrubtsoff.bankagg.domain.repository

import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.Deposit

/** The user's deposits (вклады), stored locally. */
interface DepositRepository {
    fun observeAll(): Flow<List<Deposit>>
    fun observe(id: String): Flow<Deposit?>
    suspend fun get(id: String): Deposit?
    suspend fun add(deposit: Deposit)
    suspend fun update(deposit: Deposit)
    suspend fun delete(id: String)

    /** Top-ups (F-20); [observeAll] and [observe] carry them inside each deposit. */
    suspend fun addTopUp(depositId: String, topUp: DepositTopUp)
    suspend fun deleteTopUp(id: String)
}
