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

    /** Takes a hand edit out of the log and moves the amount back by its delta (F-51). */
    suspend fun removeChange(atMs: Long): CashChange?

    /** Puts a hand edit into the log and moves the amount by its delta (F-51). */
    suspend fun addChange(change: CashChange)
}
