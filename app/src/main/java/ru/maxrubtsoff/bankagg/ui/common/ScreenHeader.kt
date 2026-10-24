package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The height of every screen's header (U-57): the title sits in the middle of it. */
val ScreenHeaderHeight: Dp = 64.dp

/** Between the header and what comes right under it — the section tabs sit at one height on every tab (U-57). */
val ScreenHeaderGap: Dp = 4.dp

/**
 * The one screen header (U-57), so every title sits at the same height on
 * every screen: a [ScreenHeaderHeight] bar right under the status bar, the
 * title in its middle, [actions] on the right.
 *
 * A tab of the bottom bar ([onBack] null) has the big title (`headlineSmall`)
 * and lives inside the screen's 16 dp margin; the tab wrapper adds the
 * status bar. A deeper screen has «Назад» first and a smaller title
 * (`titleLarge`); it is [standalone]: full width, with its own status-bar
 * inset — as a `Scaffold`'s `topBar` or the first child of the screen.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    standalone: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {}
) = ScreenHeader(onBack, modifier, standalone, actions) {
    Text(
        title,
        style = if (onBack == null && !standalone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** [ScreenHeader] with a [title] of its own (two lines, a line under it). */
@Composable
fun ScreenHeader(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    standalone: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (standalone) Modifier.statusBarsPadding() else Modifier)
            .heightIn(min = ScreenHeaderHeight)
            .padding(
                start = when {
                    onBack != null -> 4.dp
                    standalone -> 16.dp
                    else -> 0.dp
                },
                end = if (standalone) 4.dp else 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        }
        Box(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp, top = 6.dp, bottom = 6.dp)) { title() }
        actions()
    }
}
