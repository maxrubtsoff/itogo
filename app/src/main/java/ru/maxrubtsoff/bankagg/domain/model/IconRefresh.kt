package ru.maxrubtsoff.bankagg.domain.model

/**
 * Bank icons are drawn from files saved earlier (B-18) — not re-read from the
 * system on every launch. They are refreshed in the background at most once a
 * day, or right away when a bank app was installed or removed.
 */
object IconRefresh {
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /** [savedBanks]/[installedBanks] — the brand keys with an installed app then and now; null — nothing saved yet. */
    fun isStale(savedAtMs: Long?, nowMs: Long, savedBanks: Set<String>?, installedBanks: Set<String>): Boolean = when {
        savedAtMs == null || savedBanks == null -> true
        savedBanks != installedBanks -> true
        nowMs < savedAtMs -> true                  // clock went back: just redo it
        else -> nowMs - savedAtMs >= MAX_AGE_MS
    }
}
