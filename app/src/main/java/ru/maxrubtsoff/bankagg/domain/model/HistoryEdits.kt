package ru.maxrubtsoff.bankagg.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A part of the capital moved by [deltaMinor] on every saved day from [from] on (F-51). */
data class CapitalShift(val part: CapitalPart, val from: LocalDate, val deltaMinor: Long)

/** The form of the sheet of an operation: what a hand edit may change (F-51). */
data class OperationForm(
    /** The way the money went: in (income, a purchase, a top-up) or out. */
    val income: Boolean,
    /** Always positive: the sum of the operation, in its own currency. */
    val amountMinor: Long,
    val date: LocalDate,
    /** The account of an account operation. */
    val accountId: String? = null,
    /** Rubles of a currency deal, kopecks. */
    val rubMinor: Long? = null
)

/** What deleting would do, in figures: «было → станет» for the part and for the capital (F-51). */
data class DeletePreview(
    val part: CapitalPart,
    val partWas: Long,
    val partNow: Long,
    val capitalWas: Long,
    val capitalNow: Long,
    /** The first day of the chart that changes; null — the chart stays. */
    val from: LocalDate?
) {
    val changes: Boolean get() = partNow != partWas
}

/** Rules of correcting and deleting what the history lists (F-51); pure, under test. */
object HistoryEdits {

    /**
     * Whether the balance of [account] holds [tx] on top of the exact figure: only an operation the app added
     * to the bank's last balance. An operation inside the bank's own figure (or the one that gave it) is already there.
     */
    fun counted(tx: Transaction, account: Account?): Boolean {
        val checked = account?.checkedAt ?: return false
        return account.estimated && tx.postedAt > checked
    }

    /** The balance of [account] without [removed] and with [added] (each only when it is counted on it). */
    fun balanceAfter(account: Account, removed: Transaction?, added: Transaction?): Long {
        var v = account.balance.minor
        if (removed != null && removed.accountId == account.id && counted(removed, account)) v -= removed.amount.minor
        if (added != null && added.accountId == account.id && counted(added, account)) v += added.amount.minor
        return v
    }

    /** What [tx] holds in the chart: null when it holds nothing (inside the bank's balance, or the account is not counted). */
    fun shiftOf(tx: Transaction, account: Account?, rates: List<FxRate>, zone: ZoneId): CapitalShift? {
        if (account == null || account.excluded || !counted(tx, account)) return null
        val day = dayOf(tx.postedAt, zone)
        if (tx.currency == Currencies.RUB) return CapitalShift(CapitalPart.ACCOUNTS, day, tx.amount.minor)
        val rate = FxRates.latestOnOrBefore(rates, tx.currency, day)?.rubPerUnitMicro
            ?: rates.filter { it.currency == tx.currency }.minByOrNull { it.date }?.rubPerUnitMicro ?: return null
        return CapitalShift(CapitalPart.CURRENCY, day, FxMath.rubOf(tx.amount.minor, rate))
    }

    fun negate(s: CapitalShift?): List<CapitalShift> = listOfNotNull(s?.copy(deltaMinor = -s.deltaMinor))

    /** The figures of a deletion that moves [part] by [delta] today and the chart from [from]. */
    fun preview(
        part: CapitalPart, delta: Long, from: LocalDate?,
        parts: Map<CapitalPart, Long>, total: Long, included: Set<CapitalPart>
    ): DeletePreview {
        val was = parts[part] ?: 0L
        return DeletePreview(
            part, was, was + delta, total, total + if (part in included) delta else 0L,
            if (delta == 0L) null else from
        )
    }

    /** The other half of a «took and gave back» pair: the same sum, the opposite sign, the same day. */
    fun cashPartner(changes: List<CashChange>, atMs: Long, zone: ZoneId): CashChange? {
        val mine = changes.firstOrNull { it.atMs == atMs } ?: return null
        val day = dayOf(atMs, zone)
        return changes.filter { it.atMs != atMs && it.delta.minor == -mine.delta.minor && dayOf(it.atMs, zone) == day }
            .minByOrNull { kotlin.math.abs(it.atMs - atMs) }
    }

    /** [date] at the time of day of [atMs]; the same moment when the day did not change. */
    fun moveToDay(atMs: Long, date: LocalDate, zone: ZoneId): Long {
        val day = dayOf(atMs, zone)
        return if (day == date) atMs else atMs + (date.toEpochDay() - day.toEpochDay()) * DAY_MS
    }

    /** A hand edit of the cash made over [old] from the [form]. */
    fun cashChange(old: CashChange, form: OperationForm, zone: ZoneId): CashChange {
        val delta = if (form.income) form.amountMinor else -form.amountMinor
        return CashChange(moveToDay(old.atMs, form.date, zone), Money.ofMinor(old.amount.minor - old.delta.minor + delta), Money.ofMinor(delta))
    }

    /** An operation corrected from the [form]; the direction flips its kind only when the sign changes. */
    fun transaction(old: Transaction, form: OperationForm, account: Account?, zone: ZoneId): Transaction {
        val amount = if (form.income) form.amountMinor else -form.amountMinor
        val sameWay = (amount >= 0L) == (old.amount.minor >= 0L)
        return old.copy(
            amount = Money.ofMinor(amount),
            kind = if (sameWay) old.kind else if (form.income) TransactionKind.INCOME else TransactionKind.EXPENSE,
            accountId = account?.id ?: old.accountId,
            brand = account?.brand ?: old.brand,
            postedAt = moveToDay(old.postedAt, form.date, zone)
        )
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}

/** Finds the piece of a bank's text that was read as the sum (F-51). */
object NoteHighlight {

    private val NUMBER = Regex(
        """(\d{1,3}(?:[   ]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?:\s?(?:₽|руб\.?|р\.|RUB|USD|EUR|\$|€))?"""
    )

    /** The range of the first number in [text] equal to [amountMinor] (sign ignored); null — none. */
    fun amountRange(text: String, amountMinor: Long): IntRange? {
        val want = kotlin.math.abs(amountMinor)
        for (m in NUMBER.findAll(text)) {
            val whole = m.groupValues[1].filter { it.isDigit() }
            val frac = m.groupValues[2].padEnd(2, '0')
            val minor = runCatching { BigDecimal("$whole.$frac").movePointRight(2).toLong() }.getOrNull() ?: continue
            if (minor == want) return m.range
        }
        return null
    }
}
