package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/**
 * A screenshot of a bank deposit the user uploaded. The image lives as a file at
 * [imagePath]; recognition fills [draft] once and the shot's [status] drives the
 * card in the "add deposit" screen. One deposit may take several screenshots (one
 * shows the amount, another the dates) — [DepositBatch] puts them together. A
 * saved shot is deleted, so there is no "saved" state — see [ShotStatus] (shared
 * with cashback).
 */
data class DepositShot(
    val id: String,
    val imagePath: String,
    val brand: BankBrand? = null,
    val status: ShotStatus = ShotStatus.PROCESSING,
    val createdAt: Long = 0,
    val fileName: String? = null,
    val draft: DepositDraft = DepositDraft(),
    /** The deposit this shot was saved into (B-64); null while it still waits for review. */
    val depositId: String? = null
)

/** Fields the app worked out or took from an uncaptioned number, so the user should glance at them. */
enum class DepositField { START, END, PRINCIPAL, EXIT }

/**
 * Recognised (or hand-entered) deposit fields. Every field is optional until it is
 * known; [DepositParser] fills what it can read, the user completes the rest.
 */
data class DepositDraft(
    val principal: Money? = null,   // сумма на входе
    val exitAmount: Money? = null,  // сумма на выходе
    val rateBp: Int? = null,        // ставка ×100
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val income: Money? = null,      // доход за весь срок
    val title: String? = null,      // «Альфа-Вклад Максимальный»
    val method: AccrualMethod? = null,
    val guessed: Set<DepositField> = emptySet()
) {
    /** Enough to save without the user filling anything in. */
    val isComplete: Boolean
        get() = principal != null && startDate != null && endDate != null &&
            (exitAmount != null || rateBp != null)

    val isEmpty: Boolean
        get() = principal == null && exitAmount == null && rateBp == null &&
            startDate == null && endDate == null && income == null

    /** Fills a missing payout or principal from the other two of principal / payout / income. */
    fun withDerivedAmounts(): DepositDraft = when {
        exitAmount == null && principal != null && income != null ->
            copy(exitAmount = Money.ofMinor(principal.minor + income.minor))
        principal == null && exitAmount != null && income != null && exitAmount.minor > income.minor ->
            copy(principal = Money.ofMinor(exitAmount.minor - income.minor))
        else -> this
    }
}
