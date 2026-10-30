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
     * rubles, [rateMicro] the currency's rate that day (null — unknown). Several candidates, or no rate — no
     * figure for them: better silent than wrong.
     */
    fun rows(ofCurrency: List<Account>, manualRubMinor: Long, rateMicro: Long?, date: LocalDate, zone: ZoneId): List<Row> {
        val changed = onlyChanged(ofCurrency, date, zone)
        val known = rateMicro != null && rateMicro > 0L
        val manualUnits = if (known && changed != null)
            BigInteger.valueOf(manualRubMinor).multiply(BigInteger.valueOf(1_000_000L)).divide(BigInteger.valueOf(rateMicro!!)).toLong() else 0L
        val several = changed == null
        val rows = ofCurrency.map { a ->
            when {
                several -> Row(a, null, false)
                a.id == changed!!.id -> Row(a, if (known) a.balance.minor - manualUnits else null, true)
                else -> Row(a, a.balance.minor, false)
            }
        }
        return rows.sortedByDescending { it.changed }
    }
}
