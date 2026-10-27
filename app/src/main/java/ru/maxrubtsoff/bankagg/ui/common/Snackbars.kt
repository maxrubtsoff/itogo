package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import kotlin.math.roundToInt

/** U-72: the app's snackbar host; a snackbar is swiped away sideways or down. */
@Composable
fun SlitokSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> SlitokSnackbar(data) }
}

@Composable
private fun SlitokSnackbar(data: SnackbarData) {
    key(data) {
        val box = rememberSwipeToDismissBoxState(confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) data.dismiss()
            true
        })
        var dy by remember { mutableFloatStateOf(0f) }
        val threshold = with(LocalDensity.current) { 48.dp.toPx() }
        SwipeToDismissBox(
            state = box,
            backgroundContent = {},
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true
        ) {
            Snackbar(
                data,
                modifier = Modifier
                    .offset { IntOffset(0, dy.roundToInt()) }
                    .draggable(
                        rememberDraggableState { dy = (dy + it).coerceAtLeast(0f) },
                        Orientation.Vertical,
                        onDragStopped = { if (dy > threshold) data.dismiss() else dy = 0f }
                    ),
                containerColor = Slitok.PanelTop, contentColor = Slitok.Fg, actionColor = Slitok.Brass2
            )
        }
    }
}
