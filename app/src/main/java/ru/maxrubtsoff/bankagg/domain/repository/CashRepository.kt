package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CashBalance

/** Cash rubles at home: a single amount, no history. */
interface CashRepository {
    fun observe(): Flow<CashBalance?>
    suspend fun get(): CashBalance?
    suspend fun set(balance: CashBalance)
}
