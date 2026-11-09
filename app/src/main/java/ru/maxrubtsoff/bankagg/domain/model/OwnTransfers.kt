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
        val marked = transactions.filter { it.id in textMarked }.map { it.id }.toSet()
        val rest = transactions.filter { it.id !in marked }
        return marked + pairs(rest, accounts, zone).flatMap { listOf(it.first.id, it.second.id) }
    }

    /** The legs of own transfers as the list shows them: a pair (the outgoing leg first) or a lone leg. */
    fun groups(legs: List<Transaction>, accounts: List<Account>, zone: ZoneId = ZoneId.systemDefault()): List<List<Transaction>> {
        val pairs = pairs(legs, accounts, zone)
        val used = pairs.flatMap { listOf(it.first.id, it.second.id) }.toSet()
        return pairs.map { listOf(it.first, it.second) } + legs.filter { it.id !in used }.map { listOf(it) }
    }

    /** 1:1 pairs (outgoing, incoming); each outgoing takes the nearest free incoming. */
    private fun pairs(transactions: List<Transaction>, accounts: List<Account>, zone: ZoneId): List<Pair<Transaction, Transaction>> {
        val known = accounts.map { it.id }.toSet()
        val used = HashSet<String>()
        val out = ArrayList<Pair<Transaction, Transaction>>()
        val legs = transactions.filter { it.accountId in known && it.amount.minor != 0L }
        val outs = legs.filter { it.amount.minor < 0L && it.kind in OUT_KINDS }.sortedBy { it.postedAt }
        val ins = legs.filter { it.amount.minor > 0L && it.kind in IN_KINDS }
        for (o in outs) {
            val day = dayOf(o.postedAt, zone)
            val back = ins
                .filter {
                    it.id !in used && it.currency == o.currency && it.amount.minor == -o.amount.minor &&
                        it.accountId != o.accountId &&
                        (dayOf(it.postedAt, zone) == day || Math.abs(it.postedAt - o.postedAt) <= PAIR_WINDOW_MS)
                }
                .minByOrNull { Math.abs(it.postedAt - o.postedAt) } ?: continue
            used += o.id
            used += back.id
            out += o to back
        }
        return out
    }

    private fun dayOf(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
