package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import java.time.Instant
import java.time.ZoneId

/**
 * Once (B-101): the days saved while a section was switched off in «Функции» hold 0 for
 * it. They are filled roughly, from operations and CBR rates as the first build of the
 * history did; a part that has a saved value is never touched. Runs under the history lock.
 */
class BackfillCapitalParts(
    private val history: CapitalHistoryRepository,
    private val ledger: LedgerRepository,
    private val deposits: DepositRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val source: RatesSource,
    private val cash: CashRepository,
    private val settings: SettingsRepository,
    private val today: Today,
    private val lock: HistoryLock = HistoryLock()
) {
    suspend operator fun invoke() {
        if (settings.partsBackfilled()) return
        val day = today.date.value
        val saved = history.days()
        // Nothing saved yet (the first build writes every part) or no zero part: nothing to fill.
        if (saved.none { p -> p.date.isBefore(day) && CapitalPart.entries.any { (p.parts[it] ?: 0L) == 0L } }) {
            settings.markPartsBackfilled()
            return
        }
        val accounts = ledger.observeAccounts().first()
        val transactions = ledger.observeTransactions(Int.MAX_VALUE).first()
        val allDeposits = deposits.observeAll().first()
        val ops = fx.all()
        val balance = cash.get()
        val foreign = accounts.any { it.currency != Currencies.RUB }
        val first = CapitalReconstruction.firstDay(transactions, allDeposits, ops, balance, day)
        if ((ops.isNotEmpty() || foreign) && first != null) fetchPastRates(rates, source, first, day)

        val all = CapitalPart.entries.toSet()
        val rebuilt = CapitalReconstruction.build(accounts, transactions, allDeposits, ops, rates.all(), balance, all, day)
        val cashSince = balance?.takeIf { it.updatedAt > 0L }
            ?.let { Instant.ofEpochMilli(it.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        val included = settings.capitalParts.first() intersect settings.features.first().capitalParts
        lock.run {
            // Read again under the lock: a writer may have changed the days since.
            val changed = CapitalReconstruction.fillEmptyParts(history.days(), rebuilt, included, day, cashSince)
            if (changed.isNotEmpty()) history.upsertAll(changed)
        }
        settings.markPartsBackfilled()
    }
}
