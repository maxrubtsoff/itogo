package ru.maxrubtsoff.bankagg.ui

/**
 * Which way a bottom-bar switch slides: the bar is a ribbon, so a tab to the
 * right comes in from the right and one to the left from the left.
 */
object TabMotion {
    /** +1 — the target tab is to the right, -1 — to the left, 0 — not a tab switch (index < 0 or the same tab). */
    fun direction(fromTab: Int, toTab: Int): Int = when {
        fromTab < 0 || toTab < 0 || fromTab == toTab -> 0
        toTab > fromTab -> 1
        else -> -1
    }
}
