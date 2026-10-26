package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashChange

/** Cash rubles at home: a single amount and the log of its hand edits. */
interface CashRepository {
    fun observe(): Flow<CashBalance?>
    suspend fun get(): CashBalance?
    /** Hand edits, oldest first (F-48). */
    fun observeChanges(): Flow<List<CashChange>>
    suspend fun set(balance: CashBalance)
}
