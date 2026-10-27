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

/**
 * Once, when the history is still empty (the first run after the update, F-36):
 * rebuilds the past from the operations, deposits, deals and cash that are already
 * there. Currency needs the CBR rate of each past day, so the public rates are
 * fetched weekly back to the first deal; with no network the current rate stands
 * in for the past. Runs in the background and never blocks the start.
 */
class BuildCapitalHistory(
    private val history: CapitalHistoryRepository,
    private val ledger: LedgerRepository,
    private val deposits: DepositRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val source: RatesSource,
    private val cash: CashRepository,
    private val settings: SettingsRepository,
    private val today: Today
) {
    suspend operator fun invoke() {
        if (history.days().isNotEmpty()) return
        val day = today.date.value
        val accounts = ledger.observeAccounts().first()
        val transactions = ledger.observeTransactions(Int.MAX_VALUE).first()
        val allDeposits = deposits.observeAll().first()
        val ops = fx.all()
        val balance = cash.get()

        val foreign = accounts.any { it.currency != Currencies.RUB }
        val first = CapitalReconstruction.firstDay(transactions, allDeposits, ops, balance, day)
        if ((ops.isNotEmpty() || foreign) && first != null) fetchPastRates(rates, source, first, day)

        val included = settings.capitalParts.first() intersect settings.features.first().capitalParts
        val points = CapitalReconstruction.build(accounts, transactions, allDeposits, ops, rates.all(), balance, included, day)
        history.upsertAll(points)
        settings.markAccountsPastRepaired()
    }
}

/** Weekly samples of the CBR rate back to [from]; the first failure ends it quietly (the nearest known rate is used instead). */
internal suspend fun fetchPastRates(rates: RatesRepository, source: RatesSource, from: java.time.LocalDate, to: java.time.LocalDate) {
    val known = rates.all().map { it.date }.toSet()
    for (d in CapitalReconstruction.rateDays(from, to).reversed()) {
        if (d in known) continue
        val got = runCatching { source.fetch(d) }.getOrNull() ?: return
        rates.save(got)
    }
}
