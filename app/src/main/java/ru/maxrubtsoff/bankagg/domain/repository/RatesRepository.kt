package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import java.time.LocalDate

/** Cached Bank of Russia rates (table fx_rates). */
interface RatesRepository {
    fun observeAll(): Flow<List<FxRate>>
    suspend fun all(): List<FxRate>
    suspend fun save(rates: List<FxRate>)
    /** The cached rate with the latest date ≤ [date], or null. */
    suspend fun latestOnOrBefore(currency: String, date: LocalDate): FxRate?
}

/** Where official rates come from (the CBR over the network). The only network call in the app. */
interface RatesSource {
    /** All rates published for [date] (the CBR returns the last set date ≤ [date]). Throws on network errors. */
    suspend fun fetch(date: LocalDate): List<FxRate>
}
