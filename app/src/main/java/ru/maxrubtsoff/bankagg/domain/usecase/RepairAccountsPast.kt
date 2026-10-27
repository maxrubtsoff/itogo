package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today

/**
 * Once (B-109): the days saved before an account was known held its today's balance. They are
 * cut down to what the rebuilt past says ([CapitalReconstruction.capUnknown] only ever lowers),
 * so a second pass or a restored old copy changes nothing. Runs under the history lock.
 */
class RepairAccountsPast(
    private val history: CapitalHistoryRepository,
    private val ledger: LedgerRepository,
    private val deposits: DepositRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val cash: CashRepository,
    private val settings: SettingsRepository,
    private val today: Today,
    private val lock: HistoryLock = HistoryLock()
) {
    suspend operator fun invoke() {
        if (settings.accountsPastRepaired()) return
        val day = today.date.value
        if (history.days().isEmpty()) {
            settings.markAccountsPastRepaired()
            return
        }
        val accounts = ledger.observeAccounts().first()
        val transactions = ledger.observeTransactions(Int.MAX_VALUE).first()
        val all = CapitalPart.entries.toSet()
        val rebuilt = CapitalReconstruction.build(
            CapitalReconstruction.withoutBareStarts(accounts, transactions), transactions, deposits.observeAll().first(), fx.all(), rates.all(), cash.get(), all, day
        )
        val included = settings.capitalParts.first() intersect settings.features.first().capitalParts
        lock.run {
            // Read again under the lock: a writer may have changed the days since.
            val changed = CapitalReconstruction.capUnknown(history.days(), rebuilt, accounts, transactions, included, day)
            if (changed.isNotEmpty()) history.upsertAll(changed)
        }
        settings.markAccountsPastRepaired()
    }
}
