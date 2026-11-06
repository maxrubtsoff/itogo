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

    /**
     * Flat rows for SIZE / NAME: the same real categories as the bank blocks (base rate left out),
     * each keeping its bank. BANK order is [blocks]; here it falls back to the block order flattened.
     */
    fun flatRows(items: List<CashbackItem>, sort: CashbackWidgetSort): List<CashbackItem> = when (sort) {
        CashbackWidgetSort.BANK -> blocks(items).flatMap { it.rows }
        CashbackWidgetSort.NAME -> items.filterNot(CashbackSpotlight::isBase).sortedWith(
            compareBy<CashbackItem> { it.category.lowercase() }.thenByDescending { it.percent }
        )
        CashbackWidgetSort.SIZE -> items.filterNot(CashbackSpotlight::isBase).sortedWith(
            compareByDescending<CashbackItem> { it.percent }.thenBy { it.category.lowercase() }
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
