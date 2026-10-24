package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How interest on a deposit is applied over its term. Default [LINEAR].
 * - [LINEAR]         — income spread evenly across the days of the term.
 * - [CAPITALIZATION] — interest compounds monthly, so the balance curves upward
 *                      and monthly income grows toward the end.
 * - [PAYOUT]         — interest is paid out (not compounded): the deposit's own
 *                      balance stays at the principal, income accrues separately.
 */
enum class AccrualMethod { LINEAR, CAPITALIZATION, PAYOUT }

/**
 * A bank deposit (вклад). Amounts are [Money] in minor units (копейки);
 * [rateBp] is the annual rate ×100 (19,00% → 1900) to avoid storing a float.
 *
 * [exitAmount] (сумма на выходе) is the source of truth for total income when
 * present — read from the bank's screenshot or entered by hand. When it is null,
 * income is computed from [rateBp], the term and [method]. See [DepositMath].
 */
data class Deposit(
    val id: String,
    val brand: BankBrand,
    val title: String? = null,         // optional name, e.g. «Надёжный»
    val principal: Money,              // сумма на входе
    val rateBp: Int,                   // ставка годовых ×100: 19,00% → 1900
    val startDate: LocalDate,
    val endDate: LocalDate,
    val exitAmount: Money? = null,     // сумма на выходе, если известна
    val method: AccrualMethod = AccrualMethod.LINEAR,
    val createdAt: Long = 0L,
    /** Money put in after the start (F-20), each from its own day to the common end. */
    val topUps: List<DepositTopUp> = emptyList()
) {
    /** Everything put in: the opening sum plus top-ups made by [asOf]. */
    fun investedAt(asOf: LocalDate): Money =
        Money.ofMinor(principal.minor + topUps.filter { !it.date.isAfter(asOf) }.sumOf { it.amount.minor })

    /** Everything put in over the whole term, planned top-ups included. */
    val investedTotal: Money get() = Money.ofMinor(principal.minor + topUps.sumOf { it.amount.minor })

    /** Annual rate as a percentage, e.g. 19.0. */
    val ratePercent: Double get() = rateBp / 100.0

    /** Length of the deposit in whole days (never negative). */
    val termDays: Int get() =
        ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0L).toInt()
}

/** A top-up of a deposit (пополнение): money added on [date] that earns at the deposit's rate till its end. */
data class DepositTopUp(val id: String, val date: LocalDate, val amount: Money)
