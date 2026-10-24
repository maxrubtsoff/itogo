package ru.maxrubtsoff.bankagg.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Cash currency positions by the average-cost method. Operations are replayed
 * in (date, createdAt) order:
 *  - BUY:  qty += amount, cost += rub;
 *  - SELL: avg = cost / qty, costOut = avg × amount, realized += rub − costOut,
 *          qty −= amount, cost −= costOut.
 * Then value = qty × today's rate, unrealized = value − cost.
 */
object FxPortfolio {

    /** One currency's position. Value fields are null when there is no rate yet. */
    data class Position(
        val currency: String,
        val qtyMinor: Long,              // остаток в валюте, сотые
        val costMinor: Long,             // себестоимость остатка, копейки
        val realizedMinor: Long,         // зафиксированная прибыль по продажам
        val valueMinor: Long?,           // стоимость остатка по курсу сегодня
        val unrealizedMinor: Long?,      // прирост = value − cost
        val avgRate: BigDecimal?,        // средний курс покупки остатка; null при qty = 0
        val rate: FxRate?
    ) {
        /** Unrealized gain as a fraction of cost (0.034 = +3,4%); null without cost or rate. */
        val unrealizedPct: Double?
            get() = if (unrealizedMinor == null || costMinor <= 0L) null
            else unrealizedMinor.toDouble() / costMinor
    }

    /** Per-operation figure shown in the history. */
    sealed interface OpResult {
        /** A purchase: what it is worth now versus what was paid (informative). */
        data class Buy(val gainNowMinor: Long?) : OpResult
        /** A sale: profit against the average cost at that moment. */
        data class Sell(val profitMinor: Long) : OpResult
    }

    private fun ordered(ops: List<FxOperation>): List<FxOperation> =
        ops.sortedWith(compareBy<FxOperation> { it.date }.thenBy { it.createdAt })

    /** The position in [currency] built from its [ops] (other currencies are ignored). */
    fun position(currency: String, ops: List<FxOperation>, rateToday: FxRate?): Position {
        var qty = 0L
        var cost = 0L
        var realized = 0L
        for (op in ordered(ops.filter { it.currency == currency })) {
            when (op.kind) {
                FxKind.BUY -> { qty += op.amountMinor; cost += op.rubMinor }
                FxKind.SELL -> {
                    val costOut = costOut(cost, qty, op.amountMinor)
                    realized += op.rubMinor - costOut
                    qty -= op.amountMinor
                    cost -= costOut
                }
            }
        }
        val value = rateToday?.let { FxMath.rubOf(qty, it.rubPerUnitMicro) }
        return Position(
            currency = currency,
            qtyMinor = qty,
            costMinor = cost,
            realizedMinor = realized,
            valueMinor = value,
            unrealizedMinor = value?.let { it - cost },
            avgRate = if (qty > 0L) FxMath.rateOf(qty, cost) else null,
            rate = rateToday
        )
    }

    /** Every currency that has operations, each as a [Position]. */
    fun positions(ops: List<FxOperation>, rates: Map<String, FxRate>): List<Position> =
        ops.map { it.currency }.distinct().map { code -> position(code, ops, rates[code]) }

    /** Results for each operation of one currency, keyed by operation id. */
    fun results(currency: String, ops: List<FxOperation>, rateToday: FxRate?): Map<String, OpResult> {
        var qty = 0L
        var cost = 0L
        val out = HashMap<String, OpResult>()
        for (op in ordered(ops.filter { it.currency == currency })) {
            when (op.kind) {
                FxKind.BUY -> {
                    qty += op.amountMinor; cost += op.rubMinor
                    out[op.id] = OpResult.Buy(rateToday?.let { FxMath.rubOf(op.amountMinor, it.rubPerUnitMicro) - op.rubMinor })
                }
                FxKind.SELL -> {
                    val costOut = costOut(cost, qty, op.amountMinor)
                    out[op.id] = OpResult.Sell(op.rubMinor - costOut)
                    qty -= op.amountMinor
                    cost -= costOut
                }
            }
        }
        return out
    }

    /** The first sale that sells more than was held at that moment. */
    data class Shortfall(val sale: FxOperation, val availableMinor: Long)

    /**
     * Walks a currency's history and finds the first sale that the balance can't
     * cover. Null means the history is consistent. Run on every change: saving,
     * editing a date or an amount, deleting.
     */
    fun validate(ops: List<FxOperation>): Shortfall? {
        val byCurrency = ops.groupBy { it.currency }
        for ((_, list) in byCurrency) {
            var qty = 0L
            for (op in ordered(list)) {
                when (op.kind) {
                    FxKind.BUY -> qty += op.amountMinor
                    FxKind.SELL -> {
                        if (op.amountMinor > qty) return Shortfall(op, qty)
                        qty -= op.amountMinor
                    }
                }
            }
        }
        return null
    }

    /** What a change would do to the history; [Ok] or a message to show. */
    sealed interface Check {
        data object Ok : Check
        data class Blocked(val message: String) : Check
    }

    /**
     * Validates [ops] with [changed] saved (added or replacing the same id).
     * The message names the date and what was available, in the user's words.
     */
    fun checkSave(ops: List<FxOperation>, changed: FxOperation): Check {
        val after = ops.filter { it.id != changed.id } + changed
        val short = validate(after) ?: return Check.Ok
        val code = short.sale.currency
        return if (short.sale.id == changed.id) {
            Check.Blocked("На ${shortDate(short.sale.date)} было только ${FxMath.formatAmount(short.availableMinor, code)}")
        } else {
            Check.Blocked("Тогда не хватит валюты на продажу ${shortDate(short.sale.date)}")
        }
    }

    /** Validates [ops] with the operation [id] removed. */
    fun checkDelete(ops: List<FxOperation>, id: String): Check {
        val short = validate(ops.filter { it.id != id }) ?: return Check.Ok
        return Check.Blocked("Без этой покупки не хватит валюты на продажу ${shortDate(short.sale.date)}")
    }

    /** Units of [currency] available on [date] (end of day), for the sell form. */
    fun availableOn(ops: List<FxOperation>, currency: String, date: LocalDate, excludingId: String? = null): Long =
        ordered(ops.filter { it.currency == currency && it.id != excludingId && !it.date.isAfter(date) })
            .sumOf { if (it.kind == FxKind.BUY) it.amountMinor else -it.amountMinor }

    private fun costOut(cost: Long, qty: Long, amount: Long): Long =
        if (qty <= 0L) 0L
        else BigDecimal.valueOf(cost).multiply(BigDecimal.valueOf(amount))
            .divide(BigDecimal.valueOf(qty), 0, RoundingMode.HALF_UP).toLong()

    private fun shortDate(d: LocalDate): String = "%02d.%02d".format(d.dayOfMonth, d.monthValue)
}
