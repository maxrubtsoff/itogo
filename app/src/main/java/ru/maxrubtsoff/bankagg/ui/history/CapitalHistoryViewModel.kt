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
import ru.maxrubtsoff.bankagg.domain.usecase.ManualDetail
import ru.maxrubtsoff.bankagg.domain.model.OperationForm
import ru.maxrubtsoff.bankagg.domain.usecase.HistoryOperations
import ru.maxrubtsoff.bankagg.domain.usecase.OwnTransferMarks
import ru.maxrubtsoff.bankagg.domain.model.OwnTransfers
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
import java.time.YearMonth
import ru.maxrubtsoff.bankagg.domain.model.ChartWindow
import ru.maxrubtsoff.bankagg.domain.model.DaySpan
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSpend
import ru.maxrubtsoff.bankagg.domain.model.MonthSpending
import ru.maxrubtsoff.bankagg.domain.model.Spending

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

/** The card of the month's spending and the bank the list is narrowed to (F-58). */
data class SpendUi(
    /** The last months, the current one last; each with the transfers switch applied (columns). */
    val months: List<MonthSpending>,
    val selected: Int,
    val includeTransfers: Boolean,
    /** The bank the list is filtered by; null — all. */
    val bank: BankBrand?,
    /** What [bank] spent in the chosen month, for the header over the list. */
    val bankSpend: BankSpend?
) {
    val month: MonthSpending get() = months[selected]
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
    val months: List<HistoryMonth>,
    /** The spending card of the «Счета» page (F-58); null on other pages and when there is nothing spent yet. */
    val spending: SpendUi? = null,
    /** The window the person zoomed to (U-77); null — the whole period. */
    val window: DaySpan? = null,
    /** The whole period for the little map under the chart (thinned like the chart). */
    val overview: List<DayValue> = emptyList(),
    /** The days of the whole period: what the window may move in; null — no history yet. */
    val bounds: DaySpan? = null
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
    private val settings: SettingsRepository,
    private val today: Today,
    private val operations: HistoryOperations,
    private val undo: UndoCenter,
    ownMarks: OwnTransferMarks
) : ViewModel() {

    private val _period = MutableStateFlow(HistoryPeriod.MONTH)
    private val _part = MutableStateFlow<CapitalPart?>(null)
    private val _spendMonth = MutableStateFlow<YearMonth?>(null)
    private val _bank = MutableStateFlow<BankBrand?>(null)
    private val _includeTransfers = settings.spendWithTransfers

    /** A period — even the same one — shows all of it again (U-77). */
    fun setPeriod(period: HistoryPeriod) { forget(); _window.value = null; _period.value = period }

    private val _window = MutableStateFlow<DaySpan?>(null)

    /** The days of the period on the chart now (the state knows them; they are not kept on the side). */
    private val bounds: DaySpan? get() = state.value?.pages?.firstOrNull()?.bounds

    // What a gesture gave beyond whole days / whole days of length is kept for the next step, not lost.
    private var panDays = 0.0
    private var zoomRest = 1.0

    private fun move(f: (DaySpan, DaySpan) -> DaySpan) {
        val b = bounds ?: return
        val now = _window.value?.takeIf { !it.from.isBefore(b.from) && !it.to.isAfter(b.to) } ?: b
        val next = f(now, b)
        _window.value = if (ChartWindow.isWhole(next, b)) null else next
    }

    private fun forget() { panDays = 0.0; zoomRest = 1.0 }

    /** Pinch: [factor] above 1 brings closer, around [focus] (0..1 across the window). A small step waits for the next. */
    fun zoom(factor: Double, focus: Double) = move { w, b ->
        val step = ChartWindow.zoomStep(w, b, zoomRest, factor, focus)
        zoomRest = step.rest
        step.window
    }

    /** A new two-finger gesture: nothing is left over from the one before. */
    fun startGesture() = forget()

    /** Two fingers: by [shiftFraction] of the window (positive — to later days); the part of a day is kept. */
    fun pan(shiftFraction: Double) = move { w, b -> shiftDays(w, b, shiftFraction * w.days) }

    /** The little map: the window dragged by [shiftFraction] of the whole period. */
    fun panOverview(shiftFraction: Double) = move { w, b -> shiftDays(w, b, shiftFraction * b.days) }

    private fun shiftDays(w: DaySpan, b: DaySpan, days: Double): DaySpan {
        val total = panDays + days
        val whole = total.toInt()
        panDays = total - whole
        return ChartWindow.shift(w, b, whole)
    }

    /** The little map: an edge pulled to [fraction] of the whole period. */
    fun resizeWindow(startEdge: Boolean, fraction: Double) { forget(); move { w, b -> ChartWindow.resize(w, b, startEdge, fraction) } }

    /** The little map: a touch outside the window carries it there. */
    fun centerWindow(fraction: Double) { forget(); move { w, b -> ChartWindow.centerAt(w, b, fraction) } }

    /** The route's part, once; null — the total. */
    fun open(part: CapitalPart?) { _part.value = part }

    fun setPart(part: CapitalPart?) { _part.value = part }

    /** A column of the spending card (F-58). */
    fun setSpendMonth(month: YearMonth) { _spendMonth.value = month }

    /** «Переводы людям» in the card's sums; remembered (F-58). */
    fun setIncludeTransfers(on: Boolean) { viewModelScope.launch { settings.setSpendWithTransfers(on) } }

    /** Narrows the list to one bank; null — all (F-58). */
    fun setBank(bank: BankBrand?) { _bank.value = bank }

    private val inputs: Flow<Inputs> = combine(
        history.observeDays(), ledger.observeTransactions(Int.MAX_VALUE), ledger.observeAccounts(), fx.observeAll(), deposits.observeAll()
    ) { days, tx, accounts, ops, deps -> Inputs(days, tx, accounts, ops, deps, null, emptyList(), emptyList(), emptySet()) }
        .combine(cash.observe()) { i, c -> i.copy(cash = c) }
        .combine(cash.observeChanges()) { i, c -> i.copy(cashChanges = c) }
        .combine(rates.observeAll()) { i, r -> i.copy(rates = r) }
        .combine(ownMarks.observe()) { i, m -> i.copy(ownMarks = m) }

    /** What the data alone gives: the heavy part, worked out once per change of data, not per touch of a chip or a window. */
    private class Base(
        val i: Inputs, val day: LocalDate, val all: List<HistoryEvent>, val spend: List<MonthSpending>?, val transfers: Boolean
    )

    private val base: Flow<Base> = combine(inputs, today.date, _includeTransfers) { i, day, transfers ->
        val zone = java.time.ZoneId.systemDefault()
        val own = OwnTransfers.ids(i.tx, i.accounts, i.ownMarks, zone)
        val all = CapitalEvents.build(i.tx, i.accounts, i.ops, i.deps, i.cash, i.days, day, rates = i.rates, cashChanges = i.cashChanges, ownIds = own)
        val spend = if (Spending.hasAny(i.tx, i.accounts, own)) {
            Spending.window(day).map { Spending.month(i.tx, i.accounts, own, it, transfers, zone) }
        } else null
        Base(i, day, all, spend, transfers)
    }

    /** What the person chose on the screen: window, month of the card, bank. */
    private val picks = combine(_window, _spendMonth, _bank) { w, m, b -> Picks(w, m, b) }

    val state: StateFlow<HistoryScreenUi?> = combine(base, settings.features, _period, _part, picks) { base, features, period, part, pick ->
        val i = base.i
        val day = base.day
        val kept = CapitalPart.entries.filter { it in features.capitalParts }
        val all = base.all
        val long = period == HistoryPeriod.YEAR || period == HistoryPeriod.ALL
        val pages = CapitalHistory.sections(kept).map { p ->
            val full = CapitalHistory.series(i.days, period, day, p)
            val whole = ChartWindow.bounds(full)
            // A window out of the days of the period (it changed under it) is no window.
            val window = pick.window?.takeIf {
                whole != null && !ChartWindow.isWhole(it, whole) && !it.from.isBefore(whole.from) && !it.to.isAfter(whole.to)
            }
            val visible = ChartWindow.visible(full, window)
            val allShown = CapitalEvents.within(all, window?.from ?: CapitalHistory.startOf(period, i.days, day), p, window?.to)
            val spending = if (p == CapitalPart.ACCOUNTS) spendUi(base, pick) else null
            val bank = spending?.bank
            // F-58: the list narrows to one bank; the chart and its dots stay.
            val shown = if (bank == null) allShown else allShown.filter { e -> e.source == bank || e.parts.any { it.source == bank } }
            // The chart: days inside a narrow window, weeks in a wide one (U-77).
            val drawn = ChartWindow.drawn(visible)
            HistoryUi(
                period = period, part = p, kept = kept, today = day,
                series = drawn, change = CapitalHistory.change(visible),
                events = shown,
                marks = ChartMarks.of(drawn, allShown, p, CapitalHistory.average(CapitalHistory.series(i.days, period, day, CapitalPart.ACCOUNTS))),
                days = if (long) emptyList() else HistoryRows.days(shown, p),
                months = if (long) HistoryRows.months(CapitalEvents.byMonth(shown, i.days, p), p, day) else emptyList(),
                spending = spending, window = window, overview = ChartWindow.drawn(full), bounds = whole
            )
        }
        HistoryScreenUi(period, part, kept, day, pages, i.deps.map { it.id }.toSet(), features.showCaptureLog)
    }.stateIn<HistoryScreenUi?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun spendUi(base: Base, pick: Picks): SpendUi? {
        val months = base.spend ?: return null
        val selected = months.indexOfFirst { it.month == pick.month }.takeIf { it >= 0 } ?: months.lastIndex
        val bank = pick.bank
        return SpendUi(months, selected, base.transfers, bank, bank?.let { b -> months[selected].byBank.firstOrNull { it.brand == b } })
    }

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
            val was = operations.setBalance(accountId, minor, restored) ?: return@launch
            undo.offer(if (restored) "Остаток вернули" else "Остаток сохранён", restore = { operations.restoreBalance(was) })
        }
    }

    /** The first saved day a hand change of the currency moved; null — none. */
    suspend fun manualFrom(ref: HistoryRef.Manual): LocalDate? = operations.manualFrom(ref)

    suspend fun manualDetail(ref: HistoryRef.Manual): ManualDetail = operations.manualDetail(ref)

    /** Takes a hand change of the currency out of the history and offers «Вернуть» (B-121). */
    fun removeManual(ref: HistoryRef.Manual) {
        viewModelScope.launch {
            val was = operations.removeManual(ref) ?: return@launch
            undo.offer("Правка удалена", restore = { operations.restoreManual(was) })
        }
    }

    /** Puts a hand change of the currency at another sum; «Вернуть» brings the old days back. */
    fun editManual(ref: HistoryRef.Manual, minor: Long) {
        viewModelScope.launch {
            val was = operations.editManual(ref, minor) ?: return@launch
            undo.offer("Правка изменена", restore = { operations.restoreManual(was) })
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
        val cashChanges: List<CashChange>,
        val ownMarks: Set<String>
    )

    private data class Picks(val window: DaySpan?, val month: YearMonth?, val bank: BankBrand?)
}
