package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.CurrencyHistory
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today

/**
 * B-121: the «Валюта» part of every saved day recounted by [CurrencyHistory] from the deals,
 * the foreign accounts and CBR rates. Missing weekly rates are asked from the CBR (public
 * rates only); offline the cached ones stand. [ifNeeded] runs once after the update,
 * [invoke] — by the button in Настройки. Returns how many days changed.
 */
class RebuildCurrencyHistory(
    private val history: CapitalHistoryRepository,
    private val ledger: LedgerRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val source: RatesSource,
    private val settings: SettingsRepository,
    private val today: Today,
    private val lock: HistoryLock = HistoryLock()
) {
    suspend fun ifNeeded() {
        if (settings.currencyHistoryRebuilt()) return
        invoke()
        settings.markCurrencyHistoryRebuilt()
    }

    suspend operator fun invoke(): Int {
        val day = today.date.value
        val saved = history.days()
        if (saved.isEmpty()) return 0
        val accounts = ledger.observeAccounts().first()
        val transactions = ledger.observeTransactions(Int.MAX_VALUE).first()
        val ops = fx.all()
        // Rates are needed only from the first deal or the first day a foreign account is known.
        val from = (ops.map { it.date } + accounts.filter { it.currency != Currencies.RUB }
            .map { CapitalReconstruction.knownFrom(it, transactions) }).minOrNull()
        if (from != null) fetchPastRates(rates, source, maxOf(from, saved.minOf { it.date }), day)
        val allRates = rates.all()
        val included = settings.capitalParts.first() intersect settings.features.first().capitalParts
        return lock.run {
            // Read again under the lock: a writer may have changed the days since.
            val changed = CurrencyHistory.rebuild(history.days(), accounts, transactions, ops, allRates, included, day)
            if (changed.isNotEmpty()) history.upsertAll(changed)
            changed.size
        }
    }
}
