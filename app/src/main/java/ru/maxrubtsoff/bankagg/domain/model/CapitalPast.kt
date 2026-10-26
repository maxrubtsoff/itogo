package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/**
 * The saved past of the capital follows later changes (B-79, B-85): a deposit or
 * a currency deal entered with an earlier date lands on its own day, not today.
 * Every function returns only the points that changed or are new, all dated
 * before [today] — today's point belongs to the daily record.
 */
object CapitalPast {

    /** What the deposits are worth on [day] (rubles in kopecks). */
    fun depositsOn(deposits: List<Deposit>, day: LocalDate): Long = deposits
        .filter { !day.isBefore(it.startDate) && DepositMath.isActive(it, day) }
        .sumOf { DepositMath.project(it, day, seriesPoints = 2).currentValue.minor }

    /**
     * The «Вклады» part of every saved day recomputed from [deposits], and the days
     * the deposits start, are topped up and end on, given a point. Exact for the
     * list, so a second run on the result changes nothing.
     */
    fun withDeposits(points: List<CapitalPoint>, deposits: List<Deposit>, included: Set<CapitalPart>, today: LocalDate): List<CapitalPoint> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.date }
        val keyDays = deposits.flatMap { d -> listOf(d.startDate, d.endDate, d.endDate.plusDays(1)) + d.topUps.map { it.date } }.toMutableList()
        deposits.minOfOrNull { it.startDate }?.minusDays(1)?.let { if (it.isBefore(sorted.first().date)) keyDays += it }
        val added = newDays(sorted, keyDays, today)
        return (sorted.filter { inWindow(it.date, today) } + added).mapNotNull { p ->
            val now = depositsOn(deposits, p.date)
            val old = p.parts[CapitalPart.DEPOSITS] ?: 0L
            if (now == old && p !in added) null
            else p.copy(
                totalMinor = p.totalMinor + if (CapitalPart.DEPOSITS in included) now - old else 0L,
                parts = p.parts + (CapitalPart.DEPOSITS to now)
            )
        }
    }

    /**
     * Currency deals that were added, removed or changed shift «Валюта» from their
     * dates on. A delta, not a rebuild: the part holds foreign accounts too, which
     * cannot be restored exactly.
     */
    fun withFxChange(
        points: List<CapitalPoint>, before: List<FxOperation>, after: List<FxOperation>, rates: List<FxRate>,
        included: Set<CapitalPart>, today: LocalDate
    ): List<CapitalPoint> {
        if (points.isEmpty()) return emptyList()
        val gone = before.filter { it !in after }
        val came = after.filter { it !in before }
        if (gone.isEmpty() && came.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.date }
        val added = newDays(sorted, came.map { it.date }, today)
        return (sorted.filter { inWindow(it.date, today) } + added).mapNotNull { p ->
            val delta = CapitalReconstruction.fxOn(came, rates, p.date) - CapitalReconstruction.fxOn(gone, rates, p.date)
            if (delta == 0L && p !in added) null
            else p.copy(
                totalMinor = p.totalMinor + if (CapitalPart.CURRENCY in included) delta else 0L,
                parts = p.parts + (CapitalPart.CURRENCY to (p.parts[CapitalPart.CURRENCY] ?: 0L) + delta)
            )
        }
    }

    /**
     * A deleted or corrected operation or cash edit (F-51): each [shifts] entry moves its part on every saved day
     * from its date on, and its date gets a point when it has none. A delta, like [withFxChange].
     */
    fun withShifts(points: List<CapitalPoint>, shifts: List<CapitalShift>, included: Set<CapitalPart>, today: LocalDate): List<CapitalPoint> {
        if (points.isEmpty() || shifts.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.date }
        val added = newDays(sorted, shifts.map { it.from }, today)
        return (sorted.filter { inWindow(it.date, today) } + added).mapNotNull { p ->
            val mine = shifts.filter { !it.from.isAfter(p.date) }
            val byPart = mine.groupBy { it.part }.mapValues { (_, l) -> l.sumOf { it.deltaMinor } }
            if (byPart.values.all { it == 0L }) return@mapNotNull if (p in added) p else null
            p.copy(
                totalMinor = p.totalMinor + byPart.filterKeys { it in included }.values.sum(),
                parts = p.parts + byPart.map { (part, d) -> part to (p.parts[part] ?: 0L) + d }
            )
        }
    }

    private fun inWindow(day: LocalDate, today: LocalDate) =
        day.isBefore(today) && !day.isBefore(today.minusDays(CapitalReconstruction.MAX_DAYS.toLong()))

    /** Points for [days] that have none: the other parts as on the last day before (a day before the history — as on its first). */
    private fun newDays(sorted: List<CapitalPoint>, days: List<LocalDate>, today: LocalDate): List<CapitalPoint> {
        val have = sorted.map { it.date }.toSet()
        return days.distinct().filter { inWindow(it, today) && it !in have }.map { day ->
            val from = sorted.lastOrNull { it.date.isBefore(day) } ?: sorted.first()
            from.copy(date = day)
        }
    }
}
