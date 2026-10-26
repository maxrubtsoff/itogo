package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.maxrubtsoff.bankagg.domain.model.CapitalCalculator
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today

/** Live total capital: every source flow combined and handed to [CapitalCalculator]. */
class GetCapital(
    private val ledger: LedgerRepository,
    private val deposits: DepositRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val cash: CashRepository,
    private val settings: SettingsRepository,
    private val today: Today
) {
    /** [allParts]: keep the values of sections switched off in «Функции» too — for the saved days (B-101). */
    operator fun invoke(allParts: Boolean = false): Flow<CapitalSnapshot> {
        val money = combine(ledger.observeAccounts(), deposits.observeAll(), fx.observeAll(), rates.observeAll(), cash.observe()) {
            a, d, f, r, c -> Inputs(a, d, f, r, c)
        }
        // A switched-off section (F-21) is neither summed nor shown.
        return combine(money, settings.capitalParts, settings.features, today.date) { i, parts, features, day ->
            val kept = features.capitalParts
            val snap = CapitalCalculator.compute(i.accounts, i.deposits, i.fx, i.rates, i.cash, parts intersect kept, day)
            if (allParts) snap else snap.copy(parts = snap.parts.filter { it.part in kept })
        }
    }

    private data class Inputs(
        val accounts: List<ru.maxrubtsoff.bankagg.domain.model.Account>,
        val deposits: List<ru.maxrubtsoff.bankagg.domain.model.Deposit>,
        val fx: List<ru.maxrubtsoff.bankagg.domain.model.FxOperation>,
        val rates: List<ru.maxrubtsoff.bankagg.domain.model.FxRate>,
        val cash: ru.maxrubtsoff.bankagg.domain.model.CashBalance?
    )
}
