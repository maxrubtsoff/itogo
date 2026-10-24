package ru.maxrubtsoff.bankagg.domain.model

/**
 * How a batch of screenshots is laid out in groups, kept as "a,b|c" so it
 * survives rotation. Shots deleted since drop out; new ones arrive on their own.
 */
object BatchLayout {
    fun decode(s: String): List<List<String>> =
        if (s.isBlank()) emptyList() else s.split('|').map { it.split(',').filter(String::isNotBlank) }.filter { it.isNotEmpty() }

    fun encode(groups: List<List<String>>): String = groups.filter { it.isNotEmpty() }.joinToString("|") { it.joinToString(",") }

    /** [current] trimmed to [present] shots; shots not yet placed get their own group. */
    fun reconcile(current: List<List<String>>, present: List<String>): List<List<String>> {
        val kept = current.map { g -> g.filter { it in present } }.filter { it.isNotEmpty() }
        val placed = kept.flatten().toSet()
        return kept + present.filter { it !in placed }.map { listOf(it) }
    }

    /** Moves [id] into group [target], or into a group of its own when [target] is null. */
    fun move(groups: List<List<String>>, id: String, target: Int?): List<List<String>> {
        val without = groups.map { g -> g - id }
        val placed = if (target == null) without + listOf(listOf(id))
        else without.mapIndexed { i, g -> if (i == target) g + id else g }
        return placed.filter { it.isNotEmpty() }
    }
}
