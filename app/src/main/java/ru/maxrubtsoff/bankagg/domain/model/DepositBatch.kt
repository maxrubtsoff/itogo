package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Several deposit screenshots picked at once: which of them show the same deposit,
 * and the deposit they add up to. One screen often has the amount and rate, another
 * the dates — the bank name may be on neither.
 *
 * Two shots belong together when nothing they read contradicts (bank, amounts,
 * rate, dates) and they share an anchor: the same amount in, out or earned, or the
 * same end date. Pure logic, unit-tested; the user can still move a shot by hand.
 */
object DepositBatch {

    data class Item(val id: String, val brand: BankBrand?, val draft: DepositDraft)

    /** A field the shots of one group may disagree on (after a manual move). */
    enum class Part { PRINCIPAL, EXIT, RATE, START, END }

    data class Group(
        val ids: List<String>,
        val brand: BankBrand?,
        val draft: DepositDraft,
        /** Distinct values per disagreeing field, in shot order; the first one is used unless chosen. */
        val options: Map<Part, List<Any>>
    ) {
        /** Rate as read, or worked out from the amounts and dates. */
        val rateBp: Int? get() = draft.rateBp ?: impliedRateBp(draft)
        val isSavable: Boolean get() = brand != null && draft.isComplete && rateBp != null
    }

    /** Shots in order, grouped: each group is the ids of one deposit. */
    fun group(items: List<Item>): List<List<String>> {
        val groups = mutableListOf<MutableList<Item>>()
        for (item in items) {
            val home = if (item.draft.isEmpty) null
            else groups.firstOrNull { g -> g.none { it.draft.isEmpty } && fits(merge(g, emptyMap()), item) }
            if (home != null) home += item else groups += mutableListOf(item)
        }
        return groups.map { g -> g.map { it.id } }
    }

    /** The deposit a group adds up to; [chosen] picks among disagreeing values. */
    fun merge(items: List<Item>, chosen: Map<Part, Any> = emptyMap()): Group {
        val drafts = items.map { it.draft }
        val options = mutableMapOf<Part, List<Any>>()
        fun <T : Any> field(part: Part?, read: (DepositDraft) -> T?, soft: DepositField? = null): T? {
            // A value the parser worked out yields to one it read.
            val sure = drafts.filter { soft == null || soft !in it.guessed }.mapNotNull(read).distinct()
            val all = (sure + drafts.mapNotNull(read)).distinct()
            if (part != null && sure.size > 1) options[part] = sure
            @Suppress("UNCHECKED_CAST")
            return (part?.let { chosen[it] as T? }) ?: all.firstOrNull()
        }
        val start = field(Part.START, { it.startDate }, DepositField.START)
        val end = field(Part.END, { it.endDate }, DepositField.END)
        val guessed = buildSet {
            for ((f, read) in listOf<Pair<DepositField, (DepositDraft) -> Any?>>(DepositField.PRINCIPAL to { it.principal }, DepositField.EXIT to { it.exitAmount })) {
                if (drafts.none { read(it) != null && f !in it.guessed } && drafts.any { read(it) != null }) add(f)
            }
            if (start != null && drafts.none { it.startDate == start && DepositField.START !in it.guessed }) add(DepositField.START)
            if (end != null && drafts.none { it.endDate == end && DepositField.END !in it.guessed }) add(DepositField.END)
        }
        val draft = DepositDraft(
            principal = field(Part.PRINCIPAL, { it.principal }, DepositField.PRINCIPAL),
            exitAmount = field(Part.EXIT, { it.exitAmount }, DepositField.EXIT),
            rateBp = field(Part.RATE, { it.rateBp }),
            startDate = start,
            endDate = end,
            income = field(null, { it.income }),
            title = field(null, { it.title }),
            method = field(null, { it.method }),
            guessed = guessed
        ).withDerivedAmounts()
        return Group(items.map { it.id }, items.firstNotNullOfOrNull { it.brand }, draft, options)
    }

    private fun fits(g: Group, item: Item): Boolean {
        val a = g.draft
        val b = item.draft
        if (g.brand != null && item.brand != null && g.brand != item.brand) return false
        fun <T> clash(x: T?, y: T?) = x != null && y != null && x != y
        fun soft(f: DepositField) = f in a.guessed || f in b.guessed
        if ((!soft(DepositField.PRINCIPAL) && clash(a.principal, b.principal)) ||
            (!soft(DepositField.EXIT) && clash(a.exitAmount, b.exitAmount)) ||
            clash(a.rateBp, b.rateBp) || clash(a.income, b.income)
        ) return false
        if (DepositField.START !in a.guessed && DepositField.START !in b.guessed && clash(a.startDate, b.startDate)) return false
        if (DepositField.END !in a.guessed && DepositField.END !in b.guessed && clash(a.endDate, b.endDate)) return false
        fun <T> same(x: T?, y: T?) = x != null && x == y
        return (!soft(DepositField.PRINCIPAL) && same(a.principal, b.principal)) ||
            (!soft(DepositField.EXIT) && same(a.exitAmount, b.exitAmount)) ||
            same(a.income, b.income) || same(a.endDate, b.endDate)
    }

    /** Simple-interest rate implied by in, out and the term, when the screens didn't show it. */
    fun impliedRateBp(d: DepositDraft): Int? {
        val p = d.principal?.minor ?: return null
        val x = d.exitAmount?.minor ?: return null
        val s: LocalDate = d.startDate ?: return null
        val e: LocalDate = d.endDate ?: return null
        val days = ChronoUnit.DAYS.between(s, e)
        if (p <= 0 || x <= p || days <= 0) return null
        return Math.round((x - p).toDouble() / p * 365.0 / days * 10_000).toInt()
    }
}
