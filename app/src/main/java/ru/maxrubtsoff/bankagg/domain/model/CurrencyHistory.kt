package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * B-121: the «Валюта» part of the saved days, recounted from scratch. Edits and their
 * shifts left plateaus nobody can clean by hand; this throws the saved part away and
 * counts it again from what is real:
 * - cash currency — what the deals ([FxOperation]) hold at the end of the day, at the CBR
 *   rate of the day (nearest earlier known; none at all — the rate of the deal itself);
 * - foreign bank accounts — today's balance minus the operations after the day, and only
 *   from the day the account became known ([CapitalReconstruction.knownFrom]); before it 0.
 *   The past of a bare balance is not invented.
 * Only days before [today] change (today's point is the live one); other parts stay, the
 * total moves by the difference when «Валюта» is counted. Returns the changed points only.
 */
object CurrencyHistory {

    fun rebuild(
        saved: List<CapitalPoint>,
        accounts: List<Account>,
        transactions: List<Transaction>,
        fxOps: List<FxOperation>,
        rates: List<FxRate>,
        included: Set<CapitalPart>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CapitalPoint> {
        val foreign = accounts.filter { !it.excluded && it.currency != Currencies.RUB }
        val known = foreign.associate { it.id to CapitalReconstruction.knownFrom(it, transactions, zone) }
        val byAccount = transactions.groupBy { it.accountId }
        return saved.mapNotNull { p ->
            if (!p.date.isBefore(today)) return@mapNotNull null
            val value = valueOn(p.date, foreign, known, byAccount, fxOps, rates, zone)
            val old = p.parts[CapitalPart.CURRENCY] ?: 0L
            if (value == old && CapitalPart.CURRENCY in p.parts) return@mapNotNull null
            p.copy(
                totalMinor = p.totalMinor + if (CapitalPart.CURRENCY in included) value - old else 0L,
                parts = p.parts + (CapitalPart.CURRENCY to value)
            )
        }
    }

    private fun valueOn(
        day: LocalDate, foreign: List<Account>, known: Map<String, LocalDate>, byAccount: Map<String, List<Transaction>>,
        fxOps: List<FxOperation>, rates: List<FxRate>, zone: ZoneId
    ): Long {
        val after = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var sum = 0L
        foreign.forEach { a ->
            if (day.isBefore(known.getValue(a.id))) return@forEach
            val then = a.balance.minor - byAccount[a.id].orEmpty().filter { it.postedAt >= after }.sumOf { it.amount.minor }
            CapitalReconstruction.rateAt(rates, a.currency, day)?.let { sum += FxMath.rubOf(then, it) }
        }
        fxOps.map { it.currency }.distinct().forEach { code ->
            val upTo = fxOps.filter { it.currency == code && !it.date.isAfter(day) }
            val qty = upTo.sumOf { if (it.kind == FxKind.BUY) it.amountMinor else -it.amountMinor }
            if (qty == 0L) return@forEach
            val rate = CapitalReconstruction.rateAt(rates, code, day)
                ?: upTo.maxByOrNull { it.date }?.let { FxMath.micro(it.rate) }
            if (rate != null) sum += FxMath.rubOf(qty, rate)
        }
        return sum
    }

    /**
     * The CBR rates cover the past: every currency held (a deal or a foreign account) has a
     * rate no later than a week after its first day. False — offline so far: recount nothing.
     */
    fun ratesCover(
        accounts: List<Account>, transactions: List<Transaction>, fxOps: List<FxOperation>, rates: List<FxRate>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val firsts = HashMap<String, LocalDate>()
        fun note(code: String, d: LocalDate) { firsts[code] = firsts[code]?.let { minOf(it, d) } ?: d }
        fxOps.forEach { note(it.currency, it.date) }
        accounts.filter { !it.excluded && it.currency != Currencies.RUB }
            .forEach { note(it.currency, CapitalReconstruction.knownFrom(it, transactions, zone)) }
        return firsts.all { (code, first) ->
            rates.any { it.currency == code && !it.date.isAfter(first.plusDays(GRID_DAYS)) }
        }
    }

    const val GRID_DAYS = 7L
}
