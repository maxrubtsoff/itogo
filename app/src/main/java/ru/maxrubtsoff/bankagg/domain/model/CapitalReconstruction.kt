package ru.maxrubtsoff.bankagg.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The past of the capital, rebuilt once from what the phone already knows (F-36):
 * accounts from today's balance minus the operations after a day, each only from the
 * day it became known (B-109), deposits by
 * [DepositMath] from the opening day, cash currency from its deals at the CBR rate
 * of the day, cash as it is now (it was never kept before). Approximate where the
 * sources are — a balance the bank told or the user set is not an operation — and
 * exact from the day the daily points start.
 */
object CapitalReconstruction {

    /** The deepest the rebuilt past goes. */
    const val MAX_DAYS = 1100

    /** The first day there is anything to rebuild from; null — nothing at all. */
    fun firstDay(
        transactions: List<Transaction>, deposits: List<Deposit>, fxOps: List<FxOperation>,
        cash: CashBalance?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val days = transactions.map { dayOf(it.postedAt, zone) } +
            deposits.map { it.startDate } + fxOps.map { it.date } +
            listOfNotNull(cash?.takeIf { it.updatedAt > 0L }?.let { dayOf(it.updatedAt, zone) })
        val first = days.minOrNull() ?: return null
        val floor = today.minusDays(MAX_DAYS.toLong())
        return if (first.isBefore(floor)) floor else first
    }

    /** Days of weekly rate samples to ask the CBR for: from the first fx deal or foreign account day to [today]. */
    fun rateDays(from: LocalDate, today: LocalDate, stepDays: Long = 7): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = from
        while (d.isBefore(today)) { out += d; d = d.plusDays(stepDays) }
        return out
    }

    /** The day an account became known: its first operation, balance update or check, whichever is earliest. */
    fun knownFrom(account: Account, transactions: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val days = transactions.filter { it.accountId == account.id }.map { dayOf(it.postedAt, zone) } +
            listOfNotNull(
                account.updatedAt.takeIf { it > 0L }?.let { dayOf(it, zone) },
                account.checkedAt?.let { dayOf(it, zone) }
            )
        return days.minOrNull() ?: LocalDate.MIN
    }

    /** What the account held before its first known operation: balance minus all its operations. */
    fun openingOf(account: Account, transactions: List<Transaction>): Long =
        account.balance.minor - transactions.filter { it.accountId == account.id }.sumOf { it.amount.minor }

    fun build(
        accounts: List<Account>,
        transactions: List<Transaction>,
        deposits: List<Deposit>,
        fxOps: List<FxOperation>,
        rates: List<FxRate>,
        cash: CashBalance?,
        included: Set<CapitalPart>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CapitalPoint> {
        val hasAnything = accounts.isNotEmpty() || deposits.isNotEmpty() || fxOps.isNotEmpty() || cash != null
        if (!hasAnything) return emptyList()
        // One day before the first event: the line starts from what there was before it.
        val start = firstDay(transactions, deposits, fxOps, cash, today, zone)?.minusDays(1) ?: today
        val kept = accounts.filterNot { it.excluded }
        val byAccount = transactions.groupBy { it.accountId }
        val known = kept.associate { it.id to knownFrom(it, transactions, zone) }
        val cashMinor = cash?.amount?.minor ?: 0L
        val out = ArrayList<CapitalPoint>()
        var day = start
        while (!day.isAfter(today)) {
            val after = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            var accountsMinor = 0L
            var currencyMinor = 0L
            kept.forEach { a ->
                if (day.isBefore(known.getValue(a.id))) return@forEach
                val later = byAccount[a.id].orEmpty().filter { it.postedAt >= after }.sumOf { it.amount.minor }
                val then = a.balance.minor - later
                if (a.currency == Currencies.RUB) accountsMinor += then
                else rateAt(rates, a.currency, day)?.let { currencyMinor += FxMath.rubOf(then, it) }
            }
            currencyMinor += fxOn(fxOps, rates, day)
            val depositsMinor = CapitalPast.depositsOn(deposits, day)
            val parts = mapOf(
                CapitalPart.ACCOUNTS to accountsMinor, CapitalPart.DEPOSITS to depositsMinor,
                CapitalPart.CURRENCY to currencyMinor, CapitalPart.CASH to cashMinor
            )
            out += CapitalPoint(day, parts.filterKeys { it in included }.values.sum(), parts)
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * Fills the parts saved as nothing (B-101: a section switched off in «Функции» was
     * kept as 0) with the rebuilt [rebuilt] value of that day. Only days before [today]
     * and only where the saved part is 0 and the rebuilt one is not: a second pass
     * finds nothing to change. Cash was never kept before, so it is filled only from
     * [cashSince] on (null — never). Returns the changed points only.
     */
    fun fillEmptyParts(
        saved: List<CapitalPoint>, rebuilt: List<CapitalPoint>, included: Set<CapitalPart>,
        today: LocalDate, cashSince: LocalDate?
    ): List<CapitalPoint> {
        val byDay = rebuilt.associateBy { it.date }
        return saved.mapNotNull { p ->
            if (!p.date.isBefore(today)) return@mapNotNull null
            val then = byDay[p.date] ?: return@mapNotNull null
            val fill = CapitalPart.entries.filter { part ->
                (p.parts[part] ?: 0L) == 0L && (then.parts[part] ?: 0L) != 0L &&
                    (part != CapitalPart.CASH || (cashSince != null && !p.date.isBefore(cashSince)))
            }
            if (fill.isEmpty()) null
            else p.copy(
                totalMinor = p.totalMinor + fill.filter { it in included }.sumOf { then.parts[it] ?: 0L },
                parts = p.parts + fill.associateWith { then.parts[it] ?: 0L }
            )
        }
    }

    /**
     * Once (B-109): saved days earlier than the latest [knownFrom] of the counted accounts may hold
     * a balance nobody knew then. Their «Счета» and «Валюта» become the lesser of the saved and the
     * [rebuilt] value, so a second pass changes nothing and the part only ever goes down. Returns
     * the changed points only.
     */
    fun capUnknown(
        saved: List<CapitalPoint>, rebuilt: List<CapitalPoint>, accounts: List<Account>, transactions: List<Transaction>,
        included: Set<CapitalPart>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()
    ): List<CapitalPoint> {
        val lastKnown = accounts.filterNot { it.excluded }.maxOfOrNull { knownFrom(it, transactions, zone) } ?: return emptyList()
        val byDay = rebuilt.associateBy { it.date }
        val capped = listOf(CapitalPart.ACCOUNTS, CapitalPart.CURRENCY)
        return saved.mapNotNull { p ->
            if (!p.date.isBefore(today) || !p.date.isBefore(lastKnown)) return@mapNotNull null
            val then = byDay[p.date] ?: return@mapNotNull null
            val lowered = capped.filter { (p.parts[it] ?: 0L) > (then.parts[it] ?: 0L) }
            if (lowered.isEmpty()) null
            else p.copy(
                totalMinor = p.totalMinor + lowered.filter { it in included }.sumOf { (then.parts[it] ?: 0L) - (p.parts[it] ?: 0L) },
                parts = p.parts + lowered.associateWith { then.parts[it] ?: 0L }
            )
        }
    }

    /** Rubles the currency deals are worth on [day]: what is held after them, at the rate of the day. */
    internal fun fxOn(fxOps: List<FxOperation>, rates: List<FxRate>, day: LocalDate): Long =
        fxOps.map { it.currency }.distinct().sumOf { code ->
            val qty = fxOps.filter { it.currency == code && !it.date.isAfter(day) }
                .sumOf { if (it.kind == FxKind.BUY) it.amountMinor else -it.amountMinor }
            if (qty == 0L) 0L else rateAt(rates, code, day)?.let { FxMath.rubOf(qty, it) } ?: 0L
        }

    /** The rate of the day, or the nearest known one (offline the current rate stands for the whole past). */
    internal fun rateAt(rates: List<FxRate>, code: String, day: LocalDate): Long? =
        FxRates.latestOnOrBefore(rates, code, day)?.rubPerUnitMicro
            ?: rates.filter { it.currency == code }.minByOrNull { it.date }?.rubPerUnitMicro

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
