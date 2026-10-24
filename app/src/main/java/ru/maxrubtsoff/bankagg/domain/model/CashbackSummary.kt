package ru.maxrubtsoff.bankagg.domain.model

object CashbackSummary {

    /** One bank's offer for a category: used to list the runners-up below the best. */
    data class Offer(val brand: BankBrand, val percent: Double)

    /** A bank's own offer inside a row: its percent and how the bank names it ("Отели"). */
    data class NamedOffer(val brand: BankBrand, val percent: Double, val name: String)

    /**
     * One row of the "По категориям" view and the home card: a group of the
     * catalogue (its name, whatever each bank calls it) or, for text that fits no
     * group, the bank's own wording. Best percent, the bank(s) at it, the
     * runners-up, and every bank's own offer for the unfolded row.
     */
    data class CategoryRow(
        val category: String,
        val iconKey: String,
        val groupKey: String,
        val best: Double,
        val bestBanks: List<BankBrand>,   // banks sharing the best percent
        val others: List<Offer>,          // lower offers, highest first
        val offers: List<NamedOffer>      // every offer, best first
    ) {
        /** Banks with an offer here (one bank's two offers count once). */
        val bankCount: Int get() = offers.map { it.brand }.distinct().size

        /** Banks hidden behind the best ones' logos (F-19): the "+N" badge. */
        val moreBanks: Int get() = offers.map { it.brand }.distinct().count { it !in bestBanks }
    }

    /**
     * Rows join by catalogue group, so "Отели" (Ozon) and "Путешествия" (Сбер)
     * are one "Путешествия" row; text outside the catalogue keeps its own row.
     */
    fun rowKey(item: CashbackItem): String =
        if (item.groupKey == CategoryGroups.OTHER) "${CategoryGroups.OTHER}|${CategoryMatcher.normalize(item.category)}"
        else item.groupKey

    /**
     * Groups every cashback of the month into rows ([rowKey]) and, for each, finds
     * the best percent, the bank(s) offering it, and the remaining banks as
     * runners-up. Pure logic (unit-tested): the whole month is shown.
     */
    fun byCategory(items: List<CashbackItem>): List<CategoryRow> =
        items.groupBy(::rowKey)
            .map { (_, group) ->
                // A bank with two offers in one group counts with its best.
                val perBank = group.groupBy { it.brand }.mapValues { (_, l) -> l.maxOf { it.percent } }
                val best = perBank.values.max()
                val bestBanks = perBank.filterValues { it == best }.keys.sortedBy { it.title }
                val others = perBank.filterValues { it < best }
                    .map { (brand, pct) -> Offer(brand, pct) }
                    .sortedWith(compareByDescending<Offer> { it.percent }.thenBy { it.brand.title })
                val sample = group.first()
                val catalogue = sample.groupKey != CategoryGroups.OTHER
                CategoryRow(
                    category = if (catalogue) CategoryGroups.byKey(sample.groupKey).name else sample.category,
                    iconKey = CategoryGroups.byKey(sample.groupKey).iconKey,
                    groupKey = sample.groupKey,
                    best = best,
                    bestBanks = bestBanks,
                    others = others,
                    offers = group.map { NamedOffer(it.brand, it.percent, it.category) }
                        .sortedWith(compareByDescending<NamedOffer> { it.percent }.thenBy { it.brand.title }.thenBy { it.name })
                )
            }
            .sortedWith(compareByDescending<CategoryRow> { it.best }.thenBy { it.category.lowercase() })

    /** How the "По категориям" list is ordered (F-17, U-12); each has a natural direction. */
    enum class Order(val key: String) {
        /** Best percent first. */
        PERCENT("percent"),
        /** Category А→Я, Latin after Cyrillic. */
        ALPHABET("abc"),
        /** Best bank's name А→Я. */
        BANK("bank"),
        /** Most banks first. */
        BANKS("banks");

        companion object {
            fun fromKey(key: String?): Order = entries.firstOrNull { it.key == key } ?: PERCENT

            /** Stored as "percent" or "percent-" (reversed). */
            fun encode(order: SortOrder<Order>): String = order.sort.key + if (order.reversed) "-" else ""

            fun decode(stored: String?): SortOrder<Order> =
                SortOrder(fromKey(stored?.removeSuffix("-")), reversed = stored?.endsWith("-") == true)
        }
    }

    fun sort(rows: List<CategoryRow>, order: SortOrder<Order>): List<CategoryRow> {
        val natural: Comparator<CategoryRow> = when (order.sort) {
            Order.PERCENT -> compareByDescending { it.best }
            Order.ALPHABET -> compareBy { if (isCyrillic(it.category)) 0 else 1 }
            Order.BANK -> compareBy { it.bestBanks.firstOrNull()?.title?.lowercase().orEmpty() }
            Order.BANKS -> compareByDescending { it.bankCount }
        }
        val primary = if (order.reversed) natural.reversed() else natural
        val byName = compareBy<CategoryRow> { it.category.lowercase() }
        // The alphabet sort's name order turns with it; other sorts break ties А→Я.
        val tie = if (order.sort == Order.ALPHABET && order.reversed) byName.reversed() else byName
        return rows.sortedWith(primary.then(tie))
    }

    private fun isCyrillic(s: String): Boolean = s.firstOrNull { it.isLetter() }?.let { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' } ?: false
}
