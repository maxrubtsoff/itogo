package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Bumped on every tap of the bottom bar (B-36), the current tab included: a tab
 * opened from the bar shows from its top, at once, as if opened anew.
 */
val LocalTabTaps = compositionLocalOf { 0 }

/**
 * The scroll of a tab's screen (B-36). It keeps its place while you go deeper
 * and come back with «Назад», and jumps to the top — no scrolling animation —
 * when the tab is opened from the bottom bar.
 */
@Composable
fun rememberTabScrollState(): ScrollState {
    val state = rememberScrollState()
    val taps = LocalTabTaps.current
    var seen by rememberSaveable { mutableIntStateOf(taps) }
    LaunchedEffect(taps) {
        if (taps != seen) {
            state.scrollTo(0)
            seen = taps
        }
    }
    return state
}
