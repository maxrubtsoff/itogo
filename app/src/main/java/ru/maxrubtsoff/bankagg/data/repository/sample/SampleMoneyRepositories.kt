package ru.maxrubtsoff.bankagg.data.repository.sample

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.CashHistory
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.FxRates
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource

/** Bought and sold cash currency of a tour (U-54): in memory. */
class SampleFxRepository(ops: List<FxOperation>) : FxRepository {

    private val items = MutableStateFlow(ops)

    override fun observeAll(): Flow<List<FxOperation>> = items

    override suspend fun all(): List<FxOperation> = items.value

    override suspend fun get(id: String): FxOperation? = items.value.firstOrNull { it.id == id }

    override suspend fun upsert(op: FxOperation) = items.update { it.filterNot { o -> o.id == op.id } + op }

    override suspend fun delete(id: String) = items.update { list -> list.filterNot { it.id == id } }
}

/** The rates of a tour (U-54): fixed example rates, kept in memory. */
class SampleRatesRepository(rates: List<FxRate>) : RatesRepository {

    private val items = MutableStateFlow(rates)

    override fun observeAll(): Flow<List<FxRate>> = items

    override suspend fun all(): List<FxRate> = items.value

    override suspend fun save(rates: List<FxRate>) = items.update { old ->
        old.filterNot { o -> rates.any { it.currency == o.currency && it.date == o.date } } + rates
    }

    override suspend fun latestOnOrBefore(currency: String, date: LocalDate): FxRate? =
        FxRates.latestOnOrBefore(items.value, currency, date)
}

/** A tour never goes to the network: this "source" has nothing to say, so a refresh just fails quietly. */
object SampleRatesSource : RatesSource {
    override suspend fun fetch(date: LocalDate): List<FxRate> = emptyList()
}

/** Cash rubles of a tour (U-54): in memory. */
class SampleCashRepository(balance: CashBalance?) : CashRepository {

    private val state = MutableStateFlow(balance)

    override fun observe(): Flow<CashBalance?> = state

    override suspend fun get(): CashBalance? = state.value

    private val changes = MutableStateFlow<List<CashChange>>(emptyList())

    override fun observeChanges(): Flow<List<CashChange>> = changes

    override suspend fun set(balance: CashBalance) {
        CashHistory.change(state.value, balance)?.let { c -> changes.update { it + c } }
        state.value = balance
    }

    override suspend fun removeChange(atMs: Long): CashChange? {
        val row = changes.value.firstOrNull { it.atMs == atMs } ?: return null
        changes.update { l -> l.filterNot { it.atMs == atMs } }
        val cash = state.value
        val left = changes.value.maxOfOrNull { it.atMs } ?: 0L
        state.value = CashBalance(
            Money.ofMinor(((cash?.amount?.minor ?: 0L) - row.delta.minor).coerceAtLeast(0L)),
            if (cash?.updatedAt == atMs) left else cash?.updatedAt ?: left
        )
        return row
    }

    override suspend fun addChange(change: CashChange) {
        val cash = state.value
        changes.update { it + change }
        state.value = CashBalance(
            Money.ofMinor(((cash?.amount?.minor ?: 0L) + change.delta.minor).coerceAtLeast(0L)),
            maxOf(cash?.updatedAt ?: 0L, change.atMs)
        )
    }
}
