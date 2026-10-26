package ru.maxrubtsoff.bankagg.domain.model

/** How much a copy holds: the count of what the user entered or the bank told (B-91). */
data class CopyStats(val records: Int) {
    val isEmpty get() = records <= 0
}

/**
 * An empty database (after «Начать заново», or a store that did not open) must not
 * overwrite today's copy, nor push the older copies out of the rotation.
 */
object BackupGuard {

    /** [existing] is today's copy, null if there is none. */
    fun mayOverwrite(existing: CopyStats?, fresh: CopyStats): Boolean =
        !fresh.isEmpty || existing == null || existing.isEmpty

    /** Rotation drops old copies; an empty fresh one is no reason to. */
    fun mayRotate(fresh: CopyStats): Boolean = !fresh.isEmpty
}
