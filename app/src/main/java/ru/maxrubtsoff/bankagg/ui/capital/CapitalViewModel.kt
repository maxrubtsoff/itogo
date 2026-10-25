package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxPortfolio
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.usecase.GetCapital
import ru.maxrubtsoff.bankagg.domain.usecase.RefreshRates
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Total capital and everything behind the Capital tab: currency, cash, parts in the total. */
class CapitalViewModel(
    getCapital: GetCapital,
    private val settings: SettingsRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val cash: CashRepository,
    private val refreshRates: RefreshRates,
    private val today: Today,
    private val undo: UndoCenter,
    private val ledger: LedgerRepository,
    history: CapitalHistoryRepository
) : ViewModel() {

    /** Leaves accounts out of the capital or brings them back (U-18). */
    /** «Сейчас на счёте» (B-29). */
    fun setBalance(accountId: String, balanceMinor: Long) = viewModelScope.launch {
        ledger.setBalance(accountId, balanceMinor, System.currentTimeMillis())
    }

    /** The exact balance at the last check and what was added since — for the sheet's breakdown. */
    suspend fun balanceSince(account: Account): Pair<Long, Long>? {
        val at = account.checkedAt ?: return null
        val added = ledger.sumSince(account.id, at)
        return (account.balance.minor - added) to added
    }

    fun setExcluded(accountIds: List<String>, excluded: Boolean) = viewModelScope.launch {
        accountIds.forEach { ledger.setExcluded(it, excluded) }
    }


    /** Live capital; null until the first emission. */
    val snapshot: StateFlow<CapitalSnapshot?> =
        getCapital().stateIn<CapitalSnapshot?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The capital's daily points: the lines under the sums read the month from them (F-36). */
    val days: StateFlow<List<CapitalPoint>> =
        history.observeDays().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val operations: StateFlow<List<FxOperation>> =
        fx.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastSection = MutableStateFlow<CapitalPart?>(null)
    val lastSection: StateFlow<CapitalPart?> = _lastSection.asStateFlow()

    private val _lastCurrency = MutableStateFlow("USD")
    /** Default currency of a new purchase: the one used last. */
    val lastCurrency: StateFlow<String> = _lastCurrency.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for a snackbar ("Нет связи с ЦБ, курс от 18.10"). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val todayDate: StateFlow<LocalDate> get() = today.date

    init {
        viewModelScope.launch {
            _lastSection.value = settings.lastCapitalSection()
            settings.lastCurrency()?.let { _lastCurrency.value = it }
        }
    }

    // ---- parts in the total ----

    fun setPartIncluded(part: CapitalPart, included: Boolean) {
        viewModelScope.launch {
            val cur = settings.currentCapitalParts()
            settings.setCapitalParts(if (included) cur + part else cur - part)
        }
    }

    fun rememberSection(part: CapitalPart) {
        _lastSection.value = part
        viewModelScope.launch { settings.setLastCapitalSection(part) }
    }

    // ---- rates ----

    /** The ↻ button: always asks the CBR; on failure says which rate stays in use. */
    fun refreshRatesNow() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val r = refreshRates.now(moscowToday())
            _refreshing.value = false
            if (r is RefreshRates.Result.Failed) {
                val last = snapshot.value?.currency?.rateDate
                _messages.tryEmit(
                    if (last != null) "Нет связи с ЦБ, курс от ${shortDate(last)}" else "Нет связи с ЦБ"
                )
            }
        }
    }

    /** CBR rate for the purchase form's chip; null offline. */
    suspend fun cbrRateOn(currency: String, date: LocalDate): FxRate? = refreshRates.rateOn(currency, date)

    // ---- currency operations ----

    /** Checks and saves [op]; the [FxPortfolio.Check] tells the form whether it went through. */
    suspend fun saveOperation(op: FxOperation): FxPortfolio.Check {
        val check = FxPortfolio.checkSave(fx.all(), op)
        if (check is FxPortfolio.Check.Ok) {
            fx.upsert(op)
            settings.setLastCurrency(op.currency)
            _lastCurrency.value = op.currency
            // A new currency gets its rate right away.
            if (rates.latestOnOrBefore(op.currency, today.date.value) == null) refreshRates.now(moscowToday())
        }
        return check
    }

    suspend fun deleteOperation(id: String): FxPortfolio.Check {
        val op = fx.get(id) ?: return FxPortfolio.Check.Ok
        val check = FxPortfolio.checkDelete(fx.all(), id)
        if (check is FxPortfolio.Check.Ok) {
            fx.delete(id)
            undo.offer(
                if (op.kind == FxKind.BUY) "Покупка удалена" else "Продажа удалена",
                // Restore only while it still fits the portfolio (nothing sold against it since).
                restore = { if (FxPortfolio.checkSave(fx.all(), op) is FxPortfolio.Check.Ok) fx.upsert(op) }
            )
        }
        return check
    }

    fun newOperationId(): String = UUID.randomUUID().toString()

    // ---- cash ----

    fun setCash(amountMinor: Long) {
        viewModelScope.launch { cash.set(CashBalance(Money.ofMinor(amountMinor), System.currentTimeMillis())) }
    }

    private fun moscowToday(): LocalDate = LocalDate.now(ZoneId.of("Europe/Moscow"))

    private fun shortDate(d: LocalDate) = "%02d.%02d".format(d.dayOfMonth, d.monthValue)
}
