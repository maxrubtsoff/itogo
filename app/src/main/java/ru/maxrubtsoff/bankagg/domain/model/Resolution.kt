package ru.maxrubtsoff.bankagg.domain.model

/**
 * What a piece of category text resolved to.
 * - [Kind.GROUP]  — it *is* a group: [displayName] is the group's name.
 * - [Kind.AS_IS]  — kept verbatim (a brand, or nothing matched); [groupKey]
 *                   still says which group lends its icon (or [CategoryGroups.OTHER]).
 * - [Kind.EMPTY]  — nothing usable (OCR noise); the row asks the user to pick.
 */
data class Resolution(val kind: Kind, val groupKey: String, val displayName: String) {
    enum class Kind { GROUP, AS_IS, EMPTY }
}
