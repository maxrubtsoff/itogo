package ru.maxrubtsoff.bankagg.domain.model

/**
 * Saving a second screenshot of a bank that already has cashback this month.
 * [decide] says whether to ask the user ("Дополнить / Заменить"); [append]
 * builds the "Дополнить" result. "Заменить" is simply the incoming list.
 */
object CashbackMerge {

    enum class Decision { SAVE, ASK }

    /** Nothing saved yet, or the same set of offers → save without asking. */
    fun decide(existing: List<CashbackItem>, incoming: List<CashbackItem>): Decision =
        if (existing.isEmpty() ||
            existing.map { it.identityKey }.toSet() == incoming.map { it.identityKey }.toSet()
        ) Decision.SAVE else Decision.ASK

    /**
     * Existing rows first, in order (a matching incoming row updates the percent
     * but keeps the old id), then rows with new keys in incoming order.
     */
    fun append(existing: List<CashbackItem>, incoming: List<CashbackItem>): List<CashbackItem> {
        val byKey = incoming.associateBy { it.identityKey }
        val kept = existing.map { old -> byKey[old.identityKey]?.let { old.copy(percent = it.percent) } ?: old }
        val known = existing.map { it.identityKey }.toSet()
        return kept + incoming.filter { it.identityKey !in known }.distinctBy { it.identityKey }
    }

    /**
     * Editing one row may move it onto an offer the target bank already has
     * (another bank or category picked). Returns the ids of those other rows in
     * [targetRows] (the edited row's bank and month) that the edit replaces, so
     * the month never shows the same offer twice.
     */
    fun replacedByEdit(edited: CashbackItem, targetRows: List<CashbackItem>): List<String> =
        targetRows.filter { it.id != edited.id && it.identityKey == edited.identityKey }.map { it.id }
}
