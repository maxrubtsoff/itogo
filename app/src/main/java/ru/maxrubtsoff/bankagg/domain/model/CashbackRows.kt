package ru.maxrubtsoff.bankagg.domain.model

/**
 * One category — one row for a bank in a month (B-53). The same category can't
 * be picked twice in a form, a screenshot that read it twice gives one row (the
 * bigger percent stays), and nothing with duplicates ever reaches storage.
 *
 * Categories are the same when their names read the same ([CategoryMatcher.normalize]):
 * the name is what the user sees, while a group key is shared by many names
 * («Рестораны» and «Кафе» are both eating out, but they are different offers).
 */
object CashbackRows {

    fun key(name: String): String = CategoryMatcher.normalize(name)

    /** Categories already taken by other rows — the picker leaves them out. */
    fun taken(names: List<String>, exceptIndex: Int = -1): Set<String> =
        names.filterIndexed { i, name -> i != exceptIndex && name.isNotBlank() }
            .map(::key)
            .toSet()

    /** The category is already in another row of this form. */
    fun isTaken(names: List<String>, exceptIndex: Int, name: String): Boolean =
        name.isNotBlank() && key(name) in taken(names, exceptIndex)

    /**
     * Rows of one bank's month with duplicates folded together: the first place
     * of a category keeps the row, the bigger percent wins. A row without a
     * percent yet loses to one that has it.
     */
    fun <T> merge(rows: List<T>, name: (T) -> String, percent: (T) -> Double?): List<T> {
        val out = mutableListOf<T>()
        for (row in rows) {
            val rowName = name(row)
            if (rowName.isBlank()) {
                out += row
                continue
            }
            val at = out.indexOfFirst { key(name(it)) == key(rowName) }
            when {
                at < 0 -> out += row
                (percent(row) ?: -1.0) > (percent(out[at]) ?: -1.0) -> out[at] = row
            }
        }
        return out
    }
}
