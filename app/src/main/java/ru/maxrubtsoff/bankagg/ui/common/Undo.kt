package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Deleting is instant; the app-wide snackbar then offers "Вернуть" for a few
 * seconds instead of an "Удалить?" dialog beforehand.
 *
 * [restore] brings the record back; [finish] runs once it is gone for good
 * (e.g. removes a screenshot's image file kept for a possible restore).
 * Both run in the center's own scope, so they survive the screen that asked.
 */
class UndoCenter {

    class Action(
        val message: String,
        val restore: suspend () -> Unit,
        val finish: suspend () -> Unit = {},
        /** false: a plain notice, optionally with its own [actionLabel] instead of «Вернуть». */
        val undoable: Boolean = true,
        val actionLabel: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = MutableSharedFlow<Action>(extraBufferCapacity = 8)

    fun offer(message: String, restore: suspend () -> Unit, finish: suspend () -> Unit = {}) {
        actions.tryEmit(Action(message, restore, finish))
    }

    /**
     * A notice about a result (U-68): no «Вернуть», a short snackbar; with [action] the
     * snackbar has that button and [onAction] runs in the center's scope.
     */
    fun say(message: String, action: String? = null, onAction: suspend () -> Unit = {}) {
        actions.tryEmit(Action(message, restore = onAction, undoable = false, actionLabel = action))
    }

    /**
     * Shows each action in [host]. A newer deletion replaces the snackbar of an
     * older one, which then counts as final.
     */
    suspend fun showIn(host: SnackbarHostState) {
        actions.collectLatest { a ->
            var restored = false
            try {
                val r = if (a.undoable) host.showSnackbar(a.message, actionLabel = "Вернуть", duration = SnackbarDuration.Long)
                else host.showSnackbar(a.message, actionLabel = a.actionLabel, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) {
                    restored = true
                    scope.launch { a.restore() }
                }
            } finally {
                if (!restored) scope.launch { a.finish() }
            }
        }
    }
}

/** Hooks [center] to the app's snackbar host; call once at the root. */
@Composable
fun UndoSnackbarEffect(center: UndoCenter, host: SnackbarHostState) {
    LaunchedEffect(center, host) { center.showIn(host) }
}
