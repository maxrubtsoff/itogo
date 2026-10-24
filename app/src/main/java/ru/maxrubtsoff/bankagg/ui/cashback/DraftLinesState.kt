package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** Saves editor rows across process death and configuration changes. */
private val DraftLinesSaver = listSaver<SnapshotStateList<CashbackViewModel.DraftLine>, String>(
    save = { list -> list.flatMap { listOf(it.text, it.groupKey, it.percentText, it.sourceText) } },
    restore = { flat ->
        flat.chunked(4).map { CashbackViewModel.DraftLine(it[0], it[1], it[2], it[3]) }.toMutableStateList()
    }
)

/** Editable cashback rows that survive leaving the app mid-edit. */
@Composable
fun rememberDraftLines(): SnapshotStateList<CashbackViewModel.DraftLine> =
    rememberSaveable(saver = DraftLinesSaver) { mutableListOf<CashbackViewModel.DraftLine>().toMutableStateList() }
