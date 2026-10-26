package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPast
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.model.CapitalShift
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.DeletePreview
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxPortfolio
import ru.maxrubtsoff.bankagg.domain.model.HistoryEdits
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.NoteHighlight
import ru.maxrubtsoff.bankagg.domain.model.OperationForm
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the sheet of an operation shows besides the line itself (F-51). */
data class OperationDetail(
    val event: HistoryEvent,
    val bank: BankBrand?,
    val who: String?,
    val account: Account?,
    /** Accounts the operation may move to: the same currency. */
    val accounts: List<Account>,
    val balanceAfterMinor: Long?,
    /** The bank's text and the piece read as the sum; null — no notification (a hand entry, or it was trimmed). */
    val note: String?,
    val noteMark: IntRange?,
    /** The form to correct it with; null — the line is only looked at. */
    val form: OperationForm?,
    /** The cash edit that undoes this one the same day. */
    val pair: HistoryRef.Cash?,
    val currency: String?
)

/** What a deletion took out, to put it back (F-51). */
class Removed internal constructor(internal val items: List<Item>) {
    internal sealed interface Item {
        data class Tx(val tx: Transaction) : Item
        data class Cash(val change: CashChange) : Item
        data class TopUp(val depositId: String, val topUp: DepositTopUp) : Item
        data class Fx(val op: FxOperation) : Item
    }
}

/**
 * Looking at, correcting and deleting what the history lists (F-51): an account operation, a cash edit,
 * a top-up and a currency deal. The balance, the saved days of the chart and the capital follow; interest,
 * revaluation and the opening of a deposit are only looked at.
 */
class HistoryOperations(
    private val ledger: LedgerRepository,
    private val events: EventRepository,
    private val cash: CashRepository,
    private val fx: FxRepository,
    private val deposits: DepositRepository,
    private val history: CapitalHistoryRepository,
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val getCapital: GetCapital,
    private val run: TransactionRunner,
    private val today: Today,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    suspend fun detail(event: HistoryEvent): OperationDetail {
        val none = OperationDetail(event, event.bank, null, null, emptyList(), null, null, null, null, null, null)
        return when (val ref = event.ref) {
            is HistoryRef.Tx -> {
                val tx = ledger.transaction(ref.id) ?: return none
                val account = ledger.account(tx.accountId)
                val note = events.byHash(tx.id)
                val parsed = note?.let { NotificationParser.parse(it.title, it.text, tx.currency) }
                val mine = ledger.observeAccounts().first().filter { it.currency == tx.currency }
                OperationDetail(
                    event, tx.brand, tx.counterparty, account, mine, parsed?.balance?.minor,
                    note?.text, note?.let { NoteHighlight.amountRange(it.text, tx.amount.minor) },
                    OperationForm(tx.amount.minor >= 0L, kotlin.math.abs(tx.amount.minor), event.date, tx.accountId), null, tx.currency
                )
            }
            is HistoryRef.Cash -> {
                val all = cash.observeChanges().first()
                val c = all.firstOrNull { it.atMs == ref.atMs } ?: return none
                val partner = HistoryEdits.cashPartner(all, ref.atMs, zone)
                none.copy(
                    form = OperationForm(c.delta.minor >= 0L, kotlin.math.abs(c.delta.minor), event.date),
                    pair = partner?.let { HistoryRef.Cash(it.atMs) }
                )
            }
            is HistoryRef.TopUp -> {
                val up = deposits.get(ref.depositId)?.topUps?.firstOrNull { it.id == ref.topUpId } ?: return none
                none.copy(form = OperationForm(true, up.amount.minor, up.date))
            }
            is HistoryRef.Fx -> {
                val op = fx.get(ref.id) ?: return none
                none.copy(form = OperationForm(op.kind == FxKind.BUY, op.amountMinor, op.date, rubMinor = op.rubMinor), currency = op.currency)
            }
            null -> none
        }
    }

    /** The figures to show before [refs] are deleted. */
    suspend fun preview(refs: List<HistoryRef>): DeletePreview {
        val snap = getCapital().first()
        val parts = snap.parts.associate { it.part to it.valueMinor }
        val allRates = rates.all()
        var part = CapitalPart.ACCOUNTS
        var delta = 0L
        var from: LocalDate? = null
        fun note(p: CapitalPart, d: Long, day: LocalDate) {
            part = p
            delta += d
            from = from?.let { minOf(it, day) } ?: day
        }
        for (ref in refs) when (ref) {
            is HistoryRef.Tx -> ledger.transaction(ref.id)?.let { tx ->
                val shift = HistoryEdits.shiftOf(tx, ledger.account(tx.accountId), allRates, zone)
                if (shift != null) note(shift.part, -shift.deltaMinor, shift.from)
                else note(if (tx.currency == Currencies.RUB) CapitalPart.ACCOUNTS else CapitalPart.CURRENCY, 0L, dayOf(tx.postedAt))
            }
            is HistoryRef.Cash -> cash.observeChanges().first().firstOrNull { it.atMs == ref.atMs }?.let {
                note(CapitalPart.CASH, -it.delta.minor, dayOf(it.atMs))
            }
            is HistoryRef.TopUp -> deposits.get(ref.depositId)?.let { d ->
                d.topUps.firstOrNull { it.id == ref.topUpId }?.let { up ->
                    val without = d.copy(topUps = d.topUps.filter { it.id != up.id })
                    note(CapitalPart.DEPOSITS, DepositMath.valueOn(without, snap.today) - DepositMath.valueOn(d, snap.today), up.date)
                }
            }
            is HistoryRef.Fx -> fx.get(ref.id)?.let { op ->
                note(CapitalPart.CURRENCY, -CapitalReconstruction.fxOn(listOf(op), allRates, snap.today), op.date)
            }
        }
        return HistoryEdits.preview(part, delta, from, parts, snap.totalMinor, included())
    }

    /** Null when [refs] may go, else why not (a purchase that a later sale rests on). */
    suspend fun check(refs: List<HistoryRef>): String? {
        for (ref in refs) if (ref is HistoryRef.Fx) {
            (FxPortfolio.checkDelete(fx.all(), ref.id) as? FxPortfolio.Check.Blocked)?.let { return it.message }
        }
        return null
    }

    suspend fun delete(refs: List<HistoryRef>): Removed = run.run {
        val allRates = rates.all()
        val taken = ArrayList<Removed.Item>()
        val shifts = ArrayList<CapitalShift>()
        for (ref in refs) when (ref) {
            is HistoryRef.Tx -> ledger.transaction(ref.id)?.let { tx ->
                val account = ledger.account(tx.accountId)
                shifts += HistoryEdits.negate(HistoryEdits.shiftOf(tx, account, allRates, zone))
                if (account != null) ledger.setCountedBalance(account.id, HistoryEdits.balanceAfter(account, tx, null), account.estimated)
                ledger.deleteTransaction(tx.id)
                taken += Removed.Item.Tx(tx)
            }
            is HistoryRef.Cash -> cash.removeChange(ref.atMs)?.let {
                shifts += CapitalShift(CapitalPart.CASH, dayOf(it.atMs), -it.delta.minor)
                taken += Removed.Item.Cash(it)
            }
            is HistoryRef.TopUp -> deposits.get(ref.depositId)?.topUps?.firstOrNull { it.id == ref.topUpId }?.let {
                deposits.deleteTopUp(it.id)
                taken += Removed.Item.TopUp(ref.depositId, it)
            }
            is HistoryRef.Fx -> fx.get(ref.id)?.let {
                fx.delete(it.id)
                taken += Removed.Item.Fx(it)
            }
        }
        shift(shifts)
        Removed(taken)
    }

    suspend fun restore(removed: Removed): Unit = run.run {
        val allRates = rates.all()
        val shifts = ArrayList<CapitalShift>()
        for (item in removed.items) when (item) {
            is Removed.Item.Tx -> {
                ledger.addTransaction(item.tx)
                val account = ledger.account(item.tx.accountId)
                if (account != null) ledger.setCountedBalance(account.id, HistoryEdits.balanceAfter(account, null, item.tx), account.estimated)
                shifts += listOfNotNull(HistoryEdits.shiftOf(item.tx, account, allRates, zone))
            }
            is Removed.Item.Cash -> {
                cash.addChange(item.change)
                shifts += CapitalShift(CapitalPart.CASH, dayOf(item.change.atMs), item.change.delta.minor)
            }
            is Removed.Item.TopUp -> deposits.addTopUp(item.depositId, item.topUp)
            is Removed.Item.Fx -> fx.upsert(item.op)
        }
        shift(shifts)
    }

    /** Null when saved, else why not. */
    suspend fun save(ref: HistoryRef, form: OperationForm): String? {
        if (form.amountMinor <= 0L) return "Введите сумму"
        if (form.date.isAfter(today.date.value)) return "Дата не может быть в будущем"
        return run.run {
            when (ref) {
                is HistoryRef.Tx -> saveTx(ref, form)
                is HistoryRef.Cash -> {
                    val old = cash.observeChanges().first().firstOrNull { it.atMs == ref.atMs } ?: return@run null
                    val next = HistoryEdits.cashChange(old, form, zone)
                    cash.removeChange(old.atMs)
                    cash.addChange(next)
                    shift(
                        listOf(
                            CapitalShift(CapitalPart.CASH, dayOf(old.atMs), -old.delta.minor),
                            CapitalShift(CapitalPart.CASH, dayOf(next.atMs), next.delta.minor)
                        )
                    )
                    null
                }
                is HistoryRef.TopUp -> {
                    deposits.deleteTopUp(ref.topUpId)
                    deposits.addTopUp(ref.depositId, DepositTopUp(ref.topUpId, form.date, Money.ofMinor(form.amountMinor)))
                    null
                }
                is HistoryRef.Fx -> {
                    val old = fx.get(ref.id) ?: return@run null
                    val next = old.copy(
                        kind = if (form.income) FxKind.BUY else FxKind.SELL, date = form.date,
                        amountMinor = form.amountMinor, rubMinor = form.rubMinor ?: old.rubMinor
                    )
                    when (val r = FxPortfolio.checkSave(fx.all(), next)) {
                        FxPortfolio.Check.Ok -> { fx.upsert(next); null }
                        is FxPortfolio.Check.Blocked -> r.message
                    }
                }
            }
        }
    }

    private suspend fun saveTx(ref: HistoryRef.Tx, form: OperationForm): String? {
        val old = ledger.transaction(ref.id) ?: return null
        val oldAccount = ledger.account(old.accountId)
        val target = form.accountId?.let { ledger.account(it) }?.takeIf { it.currency == old.currency } ?: oldAccount
        val next = HistoryEdits.transaction(old, form, target, zone)
        val allRates = rates.all()
        ledger.updateTransaction(next)
        // The balance of the account it left and, when it moved, of the one it came to.
        oldAccount?.let { ledger.setCountedBalance(it.id, HistoryEdits.balanceAfter(it, old, next), it.estimated) }
        if (next.accountId != old.accountId) {
            ledger.account(next.accountId)?.let { ledger.setCountedBalance(it.id, HistoryEdits.balanceAfter(it, null, next), it.estimated) }
        }
        shift(
            HistoryEdits.negate(HistoryEdits.shiftOf(old, oldAccount, allRates, zone)) +
                listOfNotNull(HistoryEdits.shiftOf(next, ledger.account(next.accountId), allRates, zone))
        )
        return null
    }

    private suspend fun included(): Set<CapitalPart> =
        settings.capitalParts.first() intersect settings.features.first().capitalParts

    /** Today's point is the daily record's; the saved days before it move here. */
    private suspend fun shift(shifts: List<CapitalShift>) {
        if (shifts.isEmpty()) return
        val changed = CapitalPast.withShifts(history.days(), shifts, included(), today.date.value)
        if (changed.isNotEmpty()) history.upsertAll(changed)
    }

    private fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
