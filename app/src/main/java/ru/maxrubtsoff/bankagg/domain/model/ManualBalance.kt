package ru.maxrubtsoff.bankagg.domain.model

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A balance changed by hand (F-55): which currency and which accounts the history line «Остаток изменили вручную»
 * is about, and what the operations alone would have counted on each. The app keeps no log of hand edits, so the
 * account that was changed is told by the day its balance was last given exactly.
 */
object ManualBalance {

    /** An account of the currency with what the operations alone would have counted on it; null — not known. */
    data class Row(val account: Account, val explainedMinor: Long?, val changed: Boolean)

    /** The currency of a hand change: the only rate leg, else the only foreign currency of the counted accounts, else null. */
    fun currencyOf(legs: List<RevaluationLeg>, accounts: List<Account>): String? =
        legs.singleOrNull()?.currency
            ?: accounts.filter { !it.excluded && it.currency != Currencies.RUB }.map { it.currency }.distinct().singleOrNull()

    /** Counted accounts of [currency], by bank and title. */
    fun accountsOf(currency: String, accounts: List<Account>): List<Account> =
        accounts.filter { !it.excluded && it.currency == currency }.sortedWith(compareBy({ it.brand.title }, { it.title }))

    /** Accounts of the list whose balance was given exactly on [date]: the likely place of the change. */
    private fun touchedOn(accounts: List<Account>, date: LocalDate, zone: ZoneId): List<Account> =
        accounts.filter { a -> a.checkedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date } == true }

    /** The account the change is on when it is the only candidate (a line's subtitle); null — several or none. */
    fun onlyChanged(ofCurrency: List<Account>, date: LocalDate, zone: ZoneId): Account? =
        (touchedOn(ofCurrency, date, zone).takeIf { it.isNotEmpty() } ?: ofCurrency).singleOrNull()?.takeIf { ofCurrency.isNotEmpty() }

    /**
     * Accounts of one currency for the sheet, the changed one first. [manualRubMinor] is the line's sum in
     * rubles, [rateMicro] the currency's rate that day. «По операциям» is given only when it is sure: one account
     * had its balance given exactly that day, the rate is known, the line is a single day's ([merged] - no), and no
     * operation of that account came after the day. Otherwise the list is bare: better silent than wrong.
     */
    fun rows(
        ofCurrency: List<Account>, manualRubMinor: Long, rateMicro: Long?, date: LocalDate, zone: ZoneId,
        transactions: List<Transaction> = emptyList(), merged: Boolean = false
    ): List<Row> {
        val changed = touchedOn(ofCurrency, date, zone).singleOrNull()
        val after = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sure = changed != null && !merged && rateMicro != null && rateMicro > 0L &&
            transactions.none { it.accountId == changed.id && it.postedAt >= after }
        val units = if (sure) BigInteger.valueOf(manualRubMinor).multiply(BigInteger.valueOf(1_000_000L)).divide(BigInteger.valueOf(rateMicro!!)).toLong() else 0L
        return ofCurrency.map { a ->
            when {
                !sure -> Row(a, null, a.id == changed?.id)
                a.id == changed!!.id -> Row(a, a.balance.minor - units, true)
                else -> Row(a, a.balance.minor, false)
            }
        }.sortedByDescending { it.changed }
    }
}
