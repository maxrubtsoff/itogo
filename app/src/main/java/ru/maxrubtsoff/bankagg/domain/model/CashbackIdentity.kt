package ru.maxrubtsoff.bankagg.domain.model

/**
 * Which cashback rows are "the same offer". A row named exactly like its group
 * ("Здоровье") is the group itself; a row kept verbatim ("Lamoda", with the
 * clothing icon) is its own offer and must not merge with — or inflate — the
 * group's generic row. Used for de-duplication, best-per-category and merging.
 */
object CashbackIdentity {
    fun key(groupKey: String, displayName: String): String {
        val name = CategoryMatcher.normalize(displayName)
        return if (name == CategoryMatcher.normalize(CategoryGroups.byKey(groupKey).name)) groupKey
        else "$groupKey|$name"
    }
}

val CashbackItem.identityKey: String get() = CashbackIdentity.key(groupKey, category)
