package ru.maxrubtsoff.bankagg.domain.model

/**
 * Several cashback screenshots picked at once. Screens of the same bank and month
 * are one list (a long list often takes two screens); repeated offers collapse.
 * A screen whose bank isn't known stays on its own until the user names the bank —
 * then it joins that bank's group.
 */
object CashbackBatch {

    data class Item(val id: String, val brand: BankBrand?, val monthKey: String, val lines: List<ShotLine>)

    data class Group(val ids: List<String>, val brand: BankBrand?, val monthKey: String, val lines: List<ShotLine>) {
        /** Rows that can be saved as they are: a name and a percent. */
        val savableLines: List<ShotLine> get() = lines.filter { it.text.isNotBlank() && (it.percent ?: 0.0) > 0.0 }
    }

    /** Shots in order, grouped by bank and month; unknown banks each on their own. */
    fun group(items: List<Item>): List<List<String>> {
        val groups = linkedMapOf<Any, MutableList<String>>()
        for (item in items) {
            val key: Any = item.brand?.let { it to item.monthKey } ?: item.id
            groups.getOrPut(key) { mutableListOf() } += item.id
        }
        return groups.values.toList()
    }

    fun merge(items: List<Item>): Group = Group(
        ids = items.map { it.id },
        brand = items.firstNotNullOfOrNull { it.brand },
        monthKey = items.first().monthKey,
        lines = ShotLines.dedupe(items.flatMap { it.lines })
    )
}
