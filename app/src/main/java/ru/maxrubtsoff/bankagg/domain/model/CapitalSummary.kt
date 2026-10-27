package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/**
 * The second line of a row in the Overview summary: what the row holds on the
 * left, how it moves on the right. Every row has one (empty when there is
 * nothing to say), so all rows are of one height and the sums line up.
 *
 * [moveMinor] null — no move to show; [perMonth] — it is an income per month
 * (a bank's deposits), not the change of the last 30 days (a part).
 */
data class SummaryLine(
    val detail: String,
    val warn: Boolean = false,
    val moveMinor: Long? = null,
    val perMonth: Boolean = false
)

object CapitalSummary {

    private const val DOT = " · "

    /** The change of each part over the last 30 days from the saved history; a part with no change is left out. */
    fun partMoves(points: List<CapitalPoint>, today: LocalDate): Map<CapitalPart, Long> =
        CapitalPart.entries.mapNotNull { p ->
            CapitalHistory.glance(points, today, p)?.change?.deltaMinor?.takeIf { it != 0L }?.let { p to it }
        }.toMap()

    fun part(snap: CapitalSnapshot, part: CapitalSnapshot.Part, moves: Map<CapitalPart, Long>): SummaryLine {
        if (!part.included) return SummaryLine("не в итоге")
        val move = moves[part.part]
        return when (part.part) {
            CapitalPart.ACCOUNTS ->
                SummaryLine(plural(snap.rubAccounts.count { !it.excluded }, "счёт", "счёта", "счетов").orEmpty(), moveMinor = move)
            CapitalPart.DEPOSITS ->
                SummaryLine(plural(snap.activeDeposits.size, "вклад", "вклада", "вкладов").orEmpty(), moveMinor = move)
            CapitalPart.CURRENCY -> {
                val c = snap.currency
                if (c.missingRates.isNotEmpty()) SummaryLine("нет курса для ${c.missingRates.joinToString(", ")} — ↻", warn = true)
                // Whole units, rubles left out (U-74).
                else SummaryLine(
                    c.amountsByCurrency().joinToString(DOT) {
                        "${Currencies.symbolOf(it.currency)} ${Money.ofMinor(it.minor / 100 * 100).formatNumber()}"
                    },
                    moveMinor = move
                )
            }
            CapitalPart.CASH -> SummaryLine("", moveMinor = move)
        }
    }

    /** A bank: what it holds and its deposits' income per month; the rest: what is outside the banks. */
    fun bank(slice: BankSlice): SummaryLine {
        if (slice.isRest) return SummaryLine(
            listOfNotNull(
                "наличные".takeIf { slice.restCashMinor > 0L },
                "валюта".takeIf { slice.restCurrencyMinor > 0L }
            ).joinToString(DOT)
        )
        return SummaryLine(
            listOfNotNull(
                plural(slice.accountsCount, "счёт", "счёта", "счетов"),
                plural(slice.depositsCount, "вклад", "вклада", "вкладов")
            ).joinToString(DOT),
            moveMinor = slice.depositsMonthlyMinor.takeIf { it > 0L },
            perMonth = true
        )
    }
}
