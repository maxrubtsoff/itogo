package ru.maxrubtsoff.bankagg.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.DeletePreview
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
import ru.maxrubtsoff.bankagg.domain.model.OperationForm
import ru.maxrubtsoff.bankagg.domain.usecase.HistoryOperations
import ru.maxrubtsoff.bankagg.domain.usecase.OperationDetail
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
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
import ru.maxrubtsoff.bankagg.domain.model.ChartMark
import ru.maxrubtsoff.bankagg.domain.model.ChartMarks
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
    val aliveDeposits: Set<String>,
    /** The journal is on: a notification can be opened from the sheet of an operation (F-51). */
    val showLog: Boolean = false
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
    /** Dots of the significant actions on the chart (F-49). */
    val marks: List<ChartMark>,
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
    private val today: Today,
    private val operations: HistoryOperations,
    private val undo: UndoCenter
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
            // A year and all-time are drawn by weeks: a point stands for the week behind it (F-46).
            val drawn = CapitalHistory.thin(if (long) CapitalHistory.weekly(full) else full)
            HistoryUi(
                period = period, part = p, kept = kept, today = day,
                series = drawn, change = CapitalHistory.change(full),
                events = shown,
                marks = ChartMarks.of(drawn, shown, p, CapitalHistory.average(CapitalHistory.series(i.days, period, day, CapitalPart.ACCOUNTS))),
                days = if (long) emptyList() else HistoryRows.days(shown, p),
                months = if (long) HistoryRows.months(CapitalEvents.byMonth(shown, i.days, p), p, day) else emptyList()
            )
        }
        HistoryScreenUi(period, part, kept, day, pages, i.deps.map { it.id }.toSet(), features.showCaptureLog)
    }.stateIn<HistoryScreenUi?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The sheet of an operation (F-51): everything it shows besides the line. */
    suspend fun detail(event: HistoryEvent): OperationDetail = operations.detail(event)

    /** «Было → станет» of a deletion. */
    suspend fun preview(refs: List<HistoryRef>): DeletePreview = operations.preview(refs)

    /** Null when the deletion may go, else why not. */
    suspend fun check(refs: List<HistoryRef>): String? = operations.check(refs)

    /** Deletes at once and offers «Вернуть»; the balance, chart and capital follow by themselves. */
    fun delete(refs: List<HistoryRef>) {
        viewModelScope.launch {
            val removed = operations.delete(refs)
            undo.offer(if (refs.size > 1) "Операции удалены" else "Операция удалена", restore = { operations.restore(removed) })
        }
    }

    /** The sheet of a balance shows how the app's own count came out, like the capital's one. */
    suspend fun balanceSince(account: Account): Pair<Long, Long>? = operations.balanceSince(account)

    /** A balance put right by hand (F-55); offers «Вернуть». [restored] — the sum is the one the operations counted. */
    fun setBalance(accountId: String, minor: Long, restored: Boolean) {
        viewModelScope.launch {
            val was = operations.setBalance(accountId, minor) ?: return@launch
            undo.offer(if (restored) "Остаток вернули" else "Остаток сохранён", restore = { operations.restoreBalance(was) })
        }
    }

    /** Null when saved, else why not. */
    suspend fun save(ref: HistoryRef, form: OperationForm): String? = operations.save(ref, form)

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
