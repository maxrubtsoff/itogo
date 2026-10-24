package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.FxOperation

/** Purchases and sales of cash foreign currency. */
interface FxRepository {
    fun observeAll(): Flow<List<FxOperation>>
    suspend fun all(): List<FxOperation>
    suspend fun get(id: String): FxOperation?
    suspend fun upsert(op: FxOperation)
    suspend fun delete(id: String)
}
