package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import ru.maxrubtsoff.bankagg.ui.common.Panel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The editable list of cashback rows, shared by the screenshot screen and the
 * whole-bank editor: icon + name (tap → [CategorySearchSheet]), percent, remove.
 * "Ещё категория" adds a row and opens the picker straight away; picking moves
 * focus to the percent, closing the picker without a pick drops the empty row.
 */
@Composable
fun CashbackLinesEditor(
    lines: SnapshotStateList<CashbackViewModel.DraftLine>,
    vm: CashbackViewModel,
    tried: Boolean = false
) {
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    var pickingNew by remember { mutableStateOf(false) }
    var focusRow by remember { mutableStateOf<Int?>(null) }
    val requesters = remember { mutableListOf<FocusRequester>() }
    val focusManager = LocalFocusManager.current
    while (requesters.size < lines.size) requesters += FocusRequester()

    // U-39: the lines in one panel. B-35: nothing is red until a save attempt;
    // a named line still missing its percent only asks for it, in brass.
    if (lines.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
        Column {
            lines.forEachIndexed { i, line ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                val state = when {
                    tried && !line.isComplete -> PercentState.Error
                    line.text.isNotBlank() && line.percentText.isBlank() -> PercentState.Asked
                    else -> PercentState.Normal
                }
                CashbackLineRow(
                    iconKey = line.iconKey,
                    categoryName = line.text,
                    percentText = line.percentText,
                    onPickCategory = { pickingNew = false; pickingFor = i },
                    onPercentChange = { lines[i] = line.copy(percentText = it) },
                    onRemove = { lines.removeAt(i) },
                    percentState = state,
                    note = when {
                        state == PercentState.Error && line.text.isBlank() -> "выберите категорию или уберите строку"
                        state != PercentState.Normal && line.percentText.isBlank() -> "впишите процент"
                        else -> null
                    },
                    percentFocus = requesters[i],
                    // "Готово" jumps to the next row still missing its percent.
                    onPercentDone = {
                        val next = (i + 1 until lines.size).firstOrNull { lines[it].percentText.isBlank() }
                            ?: (0 until i).firstOrNull { lines[it].percentText.isBlank() }
                        if (next != null) focusRow = next else focusManager.clearFocus()
                    }
                )
            }
        }
    }
    AddLineButton(onClick = {
        lines.add(CashbackViewModel.DraftLine("", CategoryGroups.OTHER, ""))
        pickingNew = true
        pickingFor = lines.lastIndex
    })

    LaunchedEffect(focusRow) {
        focusRow?.let { runCatching { requesters[it].requestFocus() } }
        focusRow = null
    }

    pickingFor?.let { idx ->
        val current = lines.getOrNull(idx) ?: return@let
        CategorySearchSheet(
            // The row's current name if it has one, otherwise what the screen said.
            initialQuery = current.text.ifBlank { current.sourceText },
            suggest = vm::suggest,
            resolve = vm::resolve,
            onPick = { name, key ->
                lines[idx] = current.copy(text = name, groupKey = key)
                pickingFor = null
                if (current.percentText.isBlank()) focusRow = idx
            },
            onDismiss = {
                if (pickingNew && current.text.isBlank() && current.percentText.isBlank()) lines.removeAt(idx)
                pickingFor = null
            }
        )
    }
}
