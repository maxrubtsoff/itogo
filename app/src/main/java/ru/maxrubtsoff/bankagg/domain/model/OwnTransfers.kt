package ru.maxrubtsoff.bankagg.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Transfers between the person's own accounts (F-59): the same money moves from one account to another,
 * so it is neither a line of the history nor a spending. Worked out on the fly from both legs — nothing
 * is stored, so adding or editing one leg changes the result at once.
 */
object OwnTransfers {

    /** Legs further apart than this are two unrelated operations (unless on the same day). */
    const val PAIR_WINDOW_MS = 2 * 60 * 60 * 1000L

    private val markers = listOf(
        "перевод себе", "себе на", "между своими", "между счетами",
        "на свой счёт", "на свой счет", "на свою карту", "с вашего счёта в", "с вашего счета в"
    )

    private val OUT_KINDS = setOf(TransactionKind.TRANSFER, TransactionKind.UNKNOWN)
    private val IN_KINDS = setOf(TransactionKind.TRANSFER, TransactionKind.INCOME, TransactionKind.UNKNOWN)

    /** Whether the notification itself says the money went to the person's own account. */
    fun isOwnText(title: String?, text: String): Boolean {
        val low = (title.orEmpty() + " " + text).lowercase()
        return markers.any { it in low }
    }

    /**
     * Ids of the operations that are legs of an own transfer: those of [textMarked] (even a lone leg)
     * and 1:1 pairs of opposite equal sums between two different known accounts.
     */
    fun ids(
        transactions: List<Transaction>,
        accounts: List<Account>,
        textMarked: Set<String> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Set<String> {
        val known = accounts.map { it.id }.toSet()
        val used = HashSet<String>()
        transactions.forEach { if (it.id in textMarked) used += it.id }

        val legs = transactions.filter { it.accountId in known && it.amount.minor != 0L && it.id !in used }
        val outs = legs.filter { it.amount.minor < 0L && it.kind in OUT_KINDS }.sortedBy { it.postedAt }
        val ins = legs.filter { it.amount.minor > 0L && it.kind in IN_KINDS }
        for (out in outs) {
            val day = dayOf(out.postedAt, zone)
            val back = ins
                .filter {
                    it.id !in used && it.currency == out.currency && it.amount.minor == -out.amount.minor &&
                        it.accountId != out.accountId &&
                        (dayOf(it.postedAt, zone) == day || Math.abs(it.postedAt - out.postedAt) <= PAIR_WINDOW_MS)
                }
                .minByOrNull { Math.abs(it.postedAt - out.postedAt) } ?: continue
            used += out.id
            used += back.id
        }
        return used
    }

    private fun dayOf(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
