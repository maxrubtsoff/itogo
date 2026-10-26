package ru.maxrubtsoff.bankagg.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.CapitalChange
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.DayValue
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.HistoryDay
import ru.maxrubtsoff.bankagg.domain.model.HistoryMonth
import ru.maxrubtsoff.bankagg.domain.model.HistoryRows
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import java.time.LocalDate

/** The screen: one page per section, the total first, then each kept part (F-45). */
data class HistoryScreenUi(
    val period: HistoryPeriod,
    /** The section shown now; null is the total. */
    val part: CapitalPart?,
    val kept: List<CapitalPart>,
    val today: LocalDate,
    val pages: List<HistoryUi>,
    val aliveDeposits: Set<String>
) {
    val sections: List<CapitalPart?> get() = CapitalHistory.sections(kept)
}

/** Everything one page of the «История капитала» screen shows for the chosen period and part (F-36). */
data class HistoryUi(
    val period: HistoryPeriod,
    /** null — the total. */
    val part: CapitalPart?,
    val kept: List<CapitalPart>,
    val today: LocalDate,
    /** One value per day, thinned for the chart; empty or one day — nothing to draw yet. */
    val series: List<DayValue>,
    val change: CapitalChange?,
    val events: List<HistoryEvent>,
    /** Rows by day, for a week and a month (F-48). */
    val days: List<HistoryDay>,
    /** Rows by month, for a year and for everything (F-48). */
    val months: List<HistoryMonth>
) {
    val drawable: Boolean get() = CapitalHistory.drawable(series)
}

class CapitalHistoryViewModel(
    history: CapitalHistoryRepository,
    ledger: LedgerRepository,
    fx: FxRepository,
    rates: RatesRepository,
    deposits: DepositRepository,
    cash: CashRepository,
    settings: SettingsRepository,
    private val today: Today
) : ViewModel() {

    private val _period = MutableStateFlow(HistoryPeriod.MONTH)
    private val _part = MutableStateFlow<CapitalPart?>(null)

    fun setPeriod(period: HistoryPeriod) { _period.value = period }

    /** The route's part, once; null — the total. */
    fun open(part: CapitalPart?) { _part.value = part }

    fun setPart(part: CapitalPart?) { _part.value = part }

    private val inputs: Flow<Inputs> = combine(
        history.observeDays(), ledger.observeTransactions(Int.MAX_VALUE), ledger.observeAccounts(), fx.observeAll(), deposits.observeAll()
    ) { days, tx, accounts, ops, deps -> Inputs(days, tx, accounts, ops, deps, null, emptyList(), emptyList()) }
        .combine(cash.observe()) { i, c -> i.copy(cash = c) }
        .combine(cash.observeChanges()) { i, c -> i.copy(cashChanges = c) }
        .combine(rates.observeAll()) { i, r -> i.copy(rates = r) }

    val state: StateFlow<HistoryScreenUi?> = combine(inputs, settings.features, _period, _part, today.date) { i, features, period, part, day ->
        val kept = CapitalPart.entries.filter { it in features.capitalParts }
        val all = CapitalEvents.build(i.tx, i.accounts, i.ops, i.deps, i.cash, i.days, day, rates = i.rates, cashChanges = i.cashChanges)
        val long = period == HistoryPeriod.YEAR || period == HistoryPeriod.ALL
        val pages = CapitalHistory.sections(kept).map { p ->
            val full = CapitalHistory.series(i.days, period, day, p)
            val shown = CapitalEvents.within(all, CapitalHistory.startOf(period, i.days, day), p)
            HistoryUi(
                period = period, part = p, kept = kept, today = day,
                // A year and all-time are drawn by weeks: a point stands for the week behind it (F-46).
                series = CapitalHistory.thin(if (long) CapitalHistory.weekly(full) else full), change = CapitalHistory.change(full),
                events = shown,
                days = if (long) emptyList() else HistoryRows.days(shown, p),
                months = if (long) HistoryRows.months(CapitalEvents.byMonth(shown, i.days, p), p, day) else emptyList()
            )
        }
        HistoryScreenUi(period, part, kept, day, pages, i.deps.map { it.id }.toSet())
    }.stateIn<HistoryScreenUi?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Inputs(
        val days: List<CapitalPoint>,
        val tx: List<Transaction>,
        val accounts: List<Account>,
        val ops: List<FxOperation>,
        val deps: List<Deposit>,
        val cash: CashBalance?,
        val rates: List<FxRate>,
        val cashChanges: List<CashChange>
    )
}
