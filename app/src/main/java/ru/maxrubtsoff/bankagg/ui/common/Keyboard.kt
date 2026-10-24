package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * The body of a screen with inputs: the Scaffold padding, then shrink above the
 * keyboard (the nav-bar inset already in [padding] is not counted twice).
 */
fun Modifier.aboveKeyboard(padding: PaddingValues): Modifier =
    this.padding(padding).consumeWindowInsets(padding).imePadding()

/** True while the on-screen keyboard is up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardOpen(): Boolean = WindowInsets.isImeVisible

/**
 * A focused field stays in sight: once the keyboard has slid up (and on every
 * step of its animation), the field is scrolled back into view.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.keepVisibleWhenFocused(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) { if (focused) requester.bringIntoView() }
    // hasFocus: works on a field's container too, so its label scrolls in with it.
    this.bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }
}
