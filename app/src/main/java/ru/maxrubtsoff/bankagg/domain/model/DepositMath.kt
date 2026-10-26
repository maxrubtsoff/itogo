package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Pure accrual math for deposits — no Android, fully unit-testable. Everything the
 * deposit screens show (current value, income to date, daily/monthly accrual, the
 * growth curve and the per-month breakdown) is derived here from a [Deposit] and a
 * reference date, so the UI only draws.
 *
 * When [Deposit.exitAmount] is set it is the source of truth: total income is
 * exactly `exit − principal`, and the chosen [AccrualMethod] only shapes how that
 * income is distributed across the term. When it is null, income is computed from
 * the rate, term and method.
 */
object DepositMath {

    private const val DAYS_PER_YEAR = 365.0
    private const val DAYS_PER_MONTH = DAYS_PER_YEAR / 12.0

    enum class MonthState { PAST, CURRENT, FUTURE }

    /** Income earned within one calendar month of the term. */
    data class MonthlyAccrual(val month: YearMonth, val income: Money, val state: MonthState)

    data class Projection(
        val termDays: Int,
        val elapsedDays: Int,
        val progress: Double,          // 0..1
        val totalIncome: Money,        // доход за весь срок
        val accruedIncome: Money,      // доход, накопленный к asOf
        val currentValue: Money,       // стоимость сейчас (баланс вклада)
        val payoutAmount: Money,       // сумма к выдаче в конце срока
        val dailyAccrual: Money,       // средний доход в день
        val todayAccrual: Money,       // начисление именно за сегодняшний день
        val monthlyAccrual: Money,     // средний доход в месяц
        val valueSeries: List<Long>,   // баланс по сроку (минорные единицы) — для графика
        val monthly: List<MonthlyAccrual>
    )

    /**
     * Everything about one deposit as of [asOf]. [seriesPoints] = points in the chart curve.
     * Top-ups (F-20) add up: each is a part of its own — the same rate and method,
     * from its day to the deposit's end — and the parts' figures are summed.
     */
    fun project(deposit: Deposit, asOf: LocalDate, seriesPoints: Int = 48): Projection {
        val counted = deposit.countedTopUps
        if (counted.isEmpty()) return projectOne(deposit, asOf, seriesPoints)
        val parts = listOf(deposit.copy(topUps = emptyList())) + counted
            .map { t -> deposit.copy(id = deposit.id + "#" + t.id, principal = t.amount, startDate = t.date, exitAmount = null, topUps = emptyList()) }
        val all = parts.map { part -> part to projectOne(part, asOf, 2) }
        // A top-up not made yet is neither on the deposit nor earning.
        fun live(part: Deposit) = !asOf.isBefore(part.startDate) || part.startDate == deposit.startDate
        val base = all.first().second
        val sum = { f: (Projection) -> Long -> Money.ofMinor(all.sumOf { f(it.second) }) }
        val current = all.sumOf { (part, j) -> if (live(part)) j.currentValue.minor else 0L }
        return base.copy(
            totalIncome = sum { it.totalIncome.minor },
            accruedIncome = Money.ofMinor(all.sumOf { (part, j) -> if (live(part)) j.accruedIncome.minor else 0L }),
            currentValue = Money.ofMinor(current),
            payoutAmount = sum { it.payoutAmount.minor },
            dailyAccrual = Money.ofMinor(Math.round(all.sumOf { it.second.totalIncome.minor } / deposit.termDays.coerceAtLeast(1).toDouble())),
            todayAccrual = Money.ofMinor(all.sumOf { (part, j) -> if (live(part)) j.todayAccrual.minor else 0L }),
            monthlyAccrual = Money.ofMinor(Math.round(all.sumOf { it.second.totalIncome.minor } / (deposit.termDays.coerceAtLeast(1) / DAYS_PER_MONTH))),
            valueSeries = (0 until seriesPoints).map { i ->
                val day = deposit.startDate.plusDays(deposit.termDays.toLong() * i / (seriesPoints - 1).coerceAtLeast(1))
                parts.sumOf { part -> if (day.isBefore(part.startDate)) 0L else projectOne(part, day, 2).currentValue.minor }
            },
            monthly = base.monthly.map { m ->
                m.copy(income = Money.ofMinor(all.sumOf { (_, j) -> j.monthly.firstOrNull { it.month == m.month }?.income?.minor ?: 0L }))
            }
        )
    }

    private fun projectOne(deposit: Deposit, asOf: LocalDate, seriesPoints: Int): Projection {
        val p = deposit.principal.minor
        val r = deposit.rateBp / 10000.0
        val term = deposit.termDays
        val method = deposit.method

        val elapsed = elapsedAt(deposit, asOf)
        val progress = when {
            term <= 0 -> if (!asOf.isBefore(deposit.endDate)) 1.0 else 0.0
            else -> elapsed.toDouble() / term
        }.coerceIn(0.0, 1.0)

        val total = deposit.exitAmount?.let { it.minor - p } ?: computedIncome(p, r, term, method)

        val accrued = accruedAt(total, method, r, elapsed, term)
        val tomorrow = accruedAt(total, method, r, elapsedAt(deposit, asOf.plusDays(1)), term)
        val current = if (method == AccrualMethod.PAYOUT) p else p + accrued

        val termMonths = if (term <= 0) 1.0 else term / DAYS_PER_MONTH
        val daily = if (term <= 0) total else Math.round(total / term.toDouble())
        val monthly = Math.round(total / termMonths)

        return Projection(
            termDays = term,
            elapsedDays = elapsed,
            progress = progress,
            totalIncome = Money.ofMinor(total),
            accruedIncome = Money.ofMinor(accrued),
            currentValue = Money.ofMinor(current),
            payoutAmount = Money.ofMinor(p + total),
            dailyAccrual = Money.ofMinor(daily),
            todayAccrual = Money.ofMinor(tomorrow - accrued),
            monthlyAccrual = Money.ofMinor(monthly),
            valueSeries = valueSeries(p, total, method, r, term, seriesPoints),
            monthly = monthly(deposit, total, term, asOf)
        )
    }

    /**
     * A deposit counts (in capital and in the summary) through its last day: banks
     * usually close deposits in the evening. One that starts in the future is
     * active too — the money has already left the card.
     */
    fun isActive(deposit: Deposit, today: LocalDate): Boolean = !today.isAfter(deposit.endDate)

    /** The deposit's value on [date]: nothing before it starts (F-47). */
    fun valueOn(deposit: Deposit, date: LocalDate): Long =
        if (date.isBefore(deposit.startDate)) 0L else project(deposit, date, seriesPoints = 2).currentValue.minor

    /** Not started yet: valued at the principal, nothing accrued. */
    fun isUpcoming(deposit: Deposit, today: LocalDate): Boolean = today.isBefore(deposit.startDate)

    /** Totals across the active deposits for the deposits tab. */
    data class Summary(
        val count: Int,
        val currentValue: Money,   // стоимость всех вкладов сейчас
        val principal: Money,      // вложено
        val payout: Money,         // к выдаче
        val income: Money,         // потенциальный доход за срок
        val accrued: Money,        // накоплено к сегодня
        val dailyAccrual: Money    // начислено за сегодня
    ) {
        /** Share (0..1) of the term's income already earned — the header's progress bar. */
        val earnedShare: Double
            get() = if (income.minor <= 0) 0.0 else (accrued.minor.toDouble() / income.minor).coerceIn(0.0, 1.0)
    }

    /** Summary of the active deposits only ([isActive]); finished ones are already back on the card. */
    fun summary(deposits: List<Deposit>, asOf: LocalDate): Summary {
        var cur = 0L; var pr = 0L; var pay = 0L; var acc = 0L; var day = 0L
        val active = deposits.filter { isActive(it, asOf) }
        for (d in active) {
            val j = project(d, asOf, seriesPoints = 2)
            cur += j.currentValue.minor
            pr += d.investedTotal.minor
            pay += j.payoutAmount.minor
            acc += j.accruedIncome.minor
            day += j.todayAccrual.minor
        }
        return Summary(
            count = active.size,
            currentValue = Money.ofMinor(cur),
            principal = Money.ofMinor(pr),
            payout = Money.ofMinor(pay),
            income = Money.ofMinor(pay - pr),
            accrued = Money.ofMinor(acc),
            dailyAccrual = Money.ofMinor(day)
        )
    }

    /** Finished deposits: how many and the income they brought. */
    data class Completed(val count: Int, val income: Money)

    fun completed(deposits: List<Deposit>, asOf: LocalDate): Completed {
        val done = deposits.filter { !isActive(it, asOf) }
        val income = done.sumOf { project(it, asOf, seriesPoints = 2).totalIncome.minor }
        return Completed(done.size, Money.ofMinor(income))
    }

    /** Current value of the active deposits grouped by bank; banks without deposits are absent. */
    fun currentValueByBank(deposits: List<Deposit>, asOf: LocalDate): Map<BankBrand, Money> =
        deposits.filter { isActive(it, asOf) }.groupBy { it.brand }
            .mapValues { (_, list) ->
                Money.ofMinor(list.sumOf { project(it, asOf, seriesPoints = 2).currentValue.minor })
            }

    /**
     * 12 bars around [asOf]'s month: the current month sits in the window (a bit
     * left of centre) so past and upcoming accruals are both visible.
     */
    fun monthWindow(items: List<MonthlyAccrual>, asOf: LocalDate, size: Int = 12): List<MonthlyAccrual> {
        if (items.size <= size) return items
        val ym = YearMonth.from(asOf)
        val idx = items.indexOfFirst { it.month == ym }.let { i ->
            when {
                i >= 0 -> i
                ym.isBefore(items.first().month) -> 0
                else -> items.lastIndex
            }
        }
        val from = (idx - (size / 2 - 1)).coerceIn(0, items.size - size)
        return items.subList(from, from + size)
    }

    /**
     * One bar of the "по месяцам" chart: [height] = principal + income earned by
     * the month's end (grows for any method, payouts included); [balance] is what
     * sits on the deposit then (stays at the principal when interest is paid out).
     */
    data class MonthBar(
        val month: YearMonth,
        val state: MonthState,
        val income: Money,       // начислено за месяц
        val earned: Money,       // нарастающим итогом к концу месяца
        val balance: Money,      // на вкладе на конец месяца
        val height: Money        // высота столбика
    )

    fun monthBars(deposit: Deposit, asOf: LocalDate): List<MonthBar> {
        var earned = 0L
        return project(deposit, asOf, seriesPoints = 2).monthly.map { m ->
            earned += m.income.minor
            // What sits on the deposit by the month's end: top-ups made by then count.
            val p = deposit.investedAt(m.month.atEndOfMonth()).minor
            MonthBar(
                month = m.month,
                state = m.state,
                income = m.income,
                earned = Money.ofMinor(earned),
                balance = Money.ofMinor(if (deposit.method == AccrualMethod.PAYOUT) p else p + earned),
                height = Money.ofMinor(p + earned)
            )
        }
    }

    /** One bar's size in dp: the whole bar and its income cap on top (B-10). */
    data class BarSize(val total: Float, val cap: Float)

    /**
     * Bars stand on zero, so their heights compare honestly. Income is about a
     * percent of the bar — drawn to scale it would vanish, so the cap gets
     * [minCap] plus up to [extraCap] by the month's income against the best month:
     * always visible, and the difference between months still shows.
     */
    fun barSizes(bars: List<MonthBar>, area: Float, minCap: Float = 12f, extraCap: Float = 16f): List<BarSize> {
        val top = bars.maxOfOrNull { it.height.minor }?.takeIf { it > 0 } ?: return bars.map { BarSize(0f, 0f) }
        val best = bars.maxOf { it.income.minor }
        return bars.map { b ->
            val total = area * b.height.minor / top
            val share = if (best > 0) b.income.minor.toFloat() / best else 0f
            val cap = if (b.income.minor > 0) (minCap + extraCap * share).coerceAtMost(total) else 0f
            BarSize(total, cap)
        }
    }

    /** The coming accrual: this month's income, credited at the month's end (or the deposit's last day). */
    data class NextAccrual(val date: LocalDate, val amount: Money)

    fun nextAccrual(deposit: Deposit, asOf: LocalDate): NextAccrual? {
        if (!isActive(deposit, asOf)) return null
        val months = project(deposit, asOf, seriesPoints = 2).monthly
        val m = months.firstOrNull { it.state == MonthState.CURRENT }
            ?: months.firstOrNull { it.state == MonthState.FUTURE }
            ?: return null
        if (m.income.minor <= 0) return null
        val monthEnd = m.month.atEndOfMonth()
        return NextAccrual(minOf(monthEnd, deposit.endDate), m.income)
    }

    // ---- internals ----

    private fun elapsedAt(deposit: Deposit, date: LocalDate): Int =
        ChronoUnit.DAYS.between(deposit.startDate, date)
            .coerceIn(0L, deposit.termDays.toLong()).toInt()

    /** Income over the whole term when no exit amount is given. */
    private fun computedIncome(p: Long, r: Double, term: Int, method: AccrualMethod): Long {
        if (term <= 0 || r <= 0.0 || p <= 0L) return 0L
        return when (method) {
            AccrualMethod.CAPITALIZATION -> {
                val factor = (1.0 + r / 12.0).pow(term / DAYS_PER_MONTH)
                Math.round(p * (factor - 1.0))
            }
            else -> Math.round(p * r * term / DAYS_PER_YEAR)
        }
    }

    /** Share (0..1) of total income accrued after [e] days of a [term]-day deposit. */
    private fun fraction(method: AccrualMethod, r: Double, e: Int, term: Int): Double {
        if (term <= 0) return 1.0
        if (method != AccrualMethod.CAPITALIZATION) return (e.toDouble() / term).coerceIn(0.0, 1.0)
        val mr = r / 12.0
        val g = { x: Int -> (1.0 + mr).pow(x / DAYS_PER_MONTH) }
        val denom = g(term) - 1.0
        if (denom <= 1e-9) return (e.toDouble() / term).coerceIn(0.0, 1.0)
        return ((g(e) - 1.0) / denom).coerceIn(0.0, 1.0)
    }

    private fun accruedAt(total: Long, method: AccrualMethod, r: Double, e: Int, term: Int): Long {
        val v = Math.round(total * fraction(method, r, e, term))
        return v.coerceIn(minOf(0L, total), maxOf(0L, total))
    }

    private fun valueSeries(
        p: Long, total: Long, method: AccrualMethod, r: Double, term: Int, points: Int
    ): List<Long> {
        val n = points.coerceAtLeast(2)
        if (term <= 0) return List(n) { if (method == AccrualMethod.PAYOUT) p else p + maxOf(0L, total) }
        return (0 until n).map { i ->
            val e = Math.round(term.toDouble() * i / (n - 1)).toInt()
            if (method == AccrualMethod.PAYOUT) p else p + accruedAt(total, method, r, e, term)
        }
    }

    /**
     * Income per calendar month. A month runs from its 1st to the 1st of the next
     * month (exclusive), clipped to the term, so consecutive months share their
     * boundary and the months add up to the total exactly.
     */
    private fun monthly(deposit: Deposit, total: Long, term: Int, asOf: LocalDate): List<MonthlyAccrual> {
        val start = deposit.startDate
        val end = deposit.endDate
        if (term <= 0 || !end.isAfter(start)) {
            val ym = YearMonth.from(start)
            return listOf(MonthlyAccrual(ym, Money.ofMinor(total), stateOf(ym, asOf)))
        }
        val r = deposit.rateBp / 10000.0
        val out = ArrayList<MonthlyAccrual>()
        var ym = YearMonth.from(start)
        // The last day of the term is the payout day; it accrues nothing new.
        val last = YearMonth.from(end.minusDays(1))
        while (!ym.isAfter(last)) {
            val from = maxOf(start, ym.atDay(1))
            val to = minOf(end, ym.plusMonths(1).atDay(1))
            val eFrom = ChronoUnit.DAYS.between(start, from).coerceIn(0L, term.toLong()).toInt()
            val eTo = ChronoUnit.DAYS.between(start, to).coerceIn(0L, term.toLong()).toInt()
            val inc = accruedAt(total, deposit.method, r, eTo, term) -
                accruedAt(total, deposit.method, r, eFrom, term)
            out += MonthlyAccrual(ym, Money.ofMinor(inc), stateOf(ym, asOf))
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** A month is PAST only from the 1st of the next month; its last day is still CURRENT. */
    private fun stateOf(month: YearMonth, asOf: LocalDate): MonthState {
        val now = YearMonth.from(asOf)
        return when {
            month.isBefore(now) -> MonthState.PAST
            month.isAfter(now) -> MonthState.FUTURE
            else -> MonthState.CURRENT
        }
    }
}
