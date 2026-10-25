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
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.DayValue
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.MonthEvents
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import java.time.LocalDate

/** Everything the «История капитала» screen shows for the chosen period and part (F-36). */
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
    /** Events by month, for a year and for everything. */
    val months: List<MonthEvents>
) {
    val drawable: Boolean get() = CapitalHistory.drawable(series)
}

class CapitalHistoryViewModel(
    history: CapitalHistoryRepository,
    ledger: LedgerRepository,
    fx: FxRepository,
    deposits: DepositRepository,
    cash: CashRepository,
    settings: SettingsRepository,
    private val today: Today
) : ViewModel() {

    private val _period = MutableStateFlow(HistoryPeriod.MONTH)
    private val _part = MutableStateFlow<CapitalPart?>(null)
    private var lastPart: CapitalPart? = null

    fun setPeriod(period: HistoryPeriod) { _period.value = period }

    /** The route's part, once; null — the total. */
    fun open(part: CapitalPart?) {
        _part.value = part
        lastPart = part ?: lastPart
    }

    fun setPart(part: CapitalPart?) {
        _part.value = part
        if (part != null) lastPart = part
    }

    /** «Всего» / «По категориям»: the second returns to the part chosen last, or the first kept one. */
    fun setByParts(on: Boolean, kept: List<CapitalPart>) {
        _part.value = if (on) (lastPart?.takeIf { it in kept } ?: kept.firstOrNull()) else null
    }

    private val inputs: Flow<Inputs> = combine(
        history.observeDays(), ledger.observeTransactions(Int.MAX_VALUE), ledger.observeAccounts(), fx.observeAll(), deposits.observeAll()
    ) { days, tx, accounts, ops, deps -> Inputs(days, tx, accounts, ops, deps, null) }
        .combine(cash.observe()) { i, c -> i.copy(cash = c) }

    val state: StateFlow<HistoryUi?> = combine(inputs, settings.features, _period, _part, today.date) { i, features, period, part, day ->
        val kept = CapitalPart.entries.filter { it in features.capitalParts }
        val full = CapitalHistory.series(i.days, period, day, part)
        val all = CapitalEvents.build(i.tx, i.accounts, i.ops, i.deps, i.cash, i.days, day)
        val shown = CapitalEvents.within(all, CapitalHistory.startOf(period, i.days, day), part)
        HistoryUi(
            period = period, part = part, kept = kept, today = day,
            series = CapitalHistory.thin(full), change = CapitalHistory.change(full),
            events = shown,
            months = if (period == HistoryPeriod.YEAR || period == HistoryPeriod.ALL) CapitalEvents.byMonth(shown, i.days, part) else emptyList()
        )
    }.stateIn<HistoryUi?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Inputs(
        val days: List<CapitalPoint>,
        val tx: List<Transaction>,
        val accounts: List<Account>,
        val ops: List<FxOperation>,
        val deps: List<Deposit>,
        val cash: CashBalance?
    )
}
