package ru.maxrubtsoff.bankagg.domain.model

/** How the cashback widget orders its lines (F-56). Chosen per widget instance. */
enum class CashbackWidgetSort(val label: String) {
    BANK("Банк"), SIZE("Размер"), NAME("Название");

    companion object {
        /** Unknown or missing → [BANK]. */
        fun parse(s: String?): CashbackWidgetSort = entries.firstOrNull { it.name == s } ?: BANK
    }
}

/** Pure ordering of the widget's lines, so every mode is testable on the JVM. */
object CashbackWidgetOrder {

    /** A bank's block: its brand and regular categories (best first); the base rate is on the widget's base line. */
    data class BankGroup(val brand: BankBrand, val rows: List<CashbackItem>)

    /** Flat rows of the home-card ranking, in the chosen order. */
    fun rows(rows: List<CashbackSpotlight.Row>, sort: CashbackWidgetSort): List<CashbackSpotlight.Row> = when (sort) {
        CashbackWidgetSort.BANK -> {
            // Groups by first appearance in the ranking; inside, the ranking order.
            val order = LinkedHashMap<String, MutableList<CashbackSpotlight.Row>>()
            rows.forEach { order.getOrPut(it.banks.firstOrNull()?.title ?: "") { mutableListOf() }.add(it) }
            order.values.flatten()
        }
        CashbackWidgetSort.NAME -> rows.sortedWith(
            compareBy<CashbackSpotlight.Row> { it.title.lowercase() }.thenByDescending { it.percent }
        )
        CashbackWidgetSort.SIZE -> rows.sortedWith(
            compareByDescending<CashbackSpotlight.Row> { it.percent }.thenBy { it.title.lowercase() }
        )
    }

    /** Bank blocks: base rate left out, categories by percent; blocks by category count, then name. */
    fun blocks(items: List<CashbackItem>): List<BankGroup> =
        items.groupBy { it.brand }.map { (brand, list) ->
            val regular = list.filterNot(CashbackSpotlight::isBase)
            BankGroup(
                brand = brand,
                rows = regular.sortedWith(
                    compareByDescending<CashbackItem> { it.percent }.thenBy { it.category.lowercase() }
                )
            )
        }.sortedWith(compareByDescending<BankGroup> { it.rows.size }.thenBy { it.brand.title })
}
