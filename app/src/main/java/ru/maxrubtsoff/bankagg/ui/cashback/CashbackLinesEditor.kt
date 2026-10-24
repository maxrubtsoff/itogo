package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.CashbackRows
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.LocalTourDrive
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** What the category picker is open for. */
private sealed interface Picking {
    /** A new row: it exists only once a category is picked (B-52). */
    data object New : Picking
    /** The category of the row with this [id]. */
    data class Row(val id: String) : Picking
}

/**
 * Tall enough for any list a person types; the list sits inside a scrolling
 * screen, so it must have a limit of its own — it wraps its rows up to it.
 */
private val LIST_LIMIT = 4000.dp

/**
 * The editable list of cashback rows, shared by the screenshot screen and the
 * whole-bank editor: icon + name (tap → [CategorySearchSheet]), percent, remove.
 * "Ещё категория" opens the picker straight away; a row is added only when a
 * category is picked, comes in with the others sliding aside, and the cursor
 * goes to its percent (B-52). Closing the picker without a pick adds nothing.
 */
@Composable
fun CashbackLinesEditor(
    lines: SnapshotStateList<CashbackViewModel.DraftLine>,
    vm: CashbackViewModel,
    tried: Boolean = false,
    /** The rows that are here when the editor appears rise one after another (F-34). */
    reveal: Boolean = false
) {
    val firstRows = remember { lines.size }
    var picking by remember { mutableStateOf<Picking?>(null) }
    var focusRow by remember { mutableStateOf<String?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val focusManager = LocalFocusManager.current

    // A removed row takes its focus requester with it.
    val ids = lines.map { it.id }
    LaunchedEffect(ids) { requesters.keys.retainAll(ids.toSet()) }

    // A tour types the percent the example screenshot left unread (F-32).
    val fill = LocalTourDrive.current?.fillPercent
    LaunchedEffect(fill) {
        if (fill == null) return@LaunchedEffect
        val i = lines.indexOfFirst { it.text.isNotBlank() && it.percentText.isBlank() }
        if (i >= 0) lines[i] = lines[i].copy(percentText = fill)
    }

    // U-39: the lines in one panel. B-35: nothing is red until a save attempt;
    // a named line still missing its percent only asks for it, in brass.
    if (lines.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.heightIn(max = LIST_LIMIT)) {
            itemsIndexed(lines, key = { _, line -> line.id }) { i, line ->
                val state = when {
                    tried && !line.isComplete -> PercentState.Error
                    line.text.isNotBlank() && line.percentText.isBlank() -> PercentState.Asked
                    else -> PercentState.Normal
                }
                Column(
                    Modifier.cascade(i, reveal && i < firstRows).animateItem(
                        fadeInSpec = Motion.tween(Motion.NORMAL),
                        placementSpec = Motion.tween<IntOffset>(Motion.NORMAL, Motion.Standard),
                        fadeOutSpec = Motion.tween(Motion.FAST)
                    )
                ) {
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                    CashbackLineRow(
                        iconKey = line.iconKey,
                        categoryName = line.text,
                        percentText = line.percentText,
                        onPickCategory = { picking = Picking.Row(line.id) },
                        onPercentChange = { text -> lines.indexOfFirst { it.id == line.id }.takeIf { it >= 0 }?.let { lines[it] = lines[it].copy(percentText = text) } },
                        onRemove = { lines.removeAll { it.id == line.id } },
                        percentState = state,
                        note = when {
                            state == PercentState.Error && line.text.isBlank() -> "выберите категорию или уберите строку"
                            state != PercentState.Normal && line.percentText.isBlank() -> "впишите процент"
                            else -> null
                        },
                        percentFocus = requesters.getOrPut(line.id) { FocusRequester() },
                        // "Готово" jumps to the next row still missing its percent.
                        onPercentDone = {
                            val next = (i + 1 until lines.size).firstOrNull { lines[it].percentText.isBlank() }
                                ?: (0 until i).firstOrNull { lines[it].percentText.isBlank() }
                            if (next != null) focusRow = lines[next].id else focusManager.clearFocus()
                        },
                        modifier = Modifier.tourTarget(TourTarget.MISSING_PERCENT, active = state == PercentState.Asked, radius = 12.dp)
                    )
                }
            }
        }
    }
    AddLineButton(onClick = { picking = Picking.New })

    // The row may not be on screen yet (it was just added): ask for its percent as soon as it is.
    LaunchedEffect(focusRow) {
        val id = focusRow ?: return@LaunchedEffect
        var tries = 0
        while (tries++ < 8) {
            if (runCatching { requesters[id]?.requestFocus() ?: error("no row yet") }.isSuccess) break
            withFrameNanos { }
        }
        focusRow = null
    }

    when (val p = picking) {
        null -> Unit
        Picking.New -> CategorySearchSheet(
            initialQuery = "",
            // One category — one row (B-53).
            taken = CashbackRows.taken(lines.map { it.text }),
            suggest = vm::suggest,
            resolve = vm::resolve,
            onPick = { name, key ->
                val line = CashbackViewModel.DraftLine(name, key, "")
                lines.add(line)
                picking = null
                focusRow = line.id
            },
            onDismiss = { picking = null }
        )
        is Picking.Row -> {
            val idx = lines.indexOfFirst { it.id == p.id }
            val current = lines.getOrNull(idx)
            if (current != null) CategorySearchSheet(
                // The row's current name if it has one, otherwise what the screen said.
                initialQuery = current.text.ifBlank { current.sourceText },
                taken = CashbackRows.taken(lines.map { it.text }, exceptIndex = idx),
                suggest = vm::suggest,
                resolve = vm::resolve,
                onPick = { name, key ->
                    // The list may have changed while the sheet was open (a tour types a percent): find the row by id now.
                    val at = lines.indexOfFirst { it.id == p.id }
                    if (at >= 0) {
                        val row = lines[at]
                        lines[at] = row.copy(text = name, groupKey = key)
                        if (row.percentText.isBlank()) focusRow = row.id
                    }
                    picking = null
                },
                onDismiss = { picking = null }
            )
        }
    }
}
