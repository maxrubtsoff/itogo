package ru.maxrubtsoff.bankagg.ui.cashback

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BatchLayout
import ru.maxrubtsoff.bankagg.domain.model.CashbackBatch
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.BatchProgress
import ru.maxrubtsoff.bankagg.ui.common.MoveShotSheet
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.ShotDragLayer
import ru.maxrubtsoff.bankagg.ui.common.ShotDragState
import ru.maxrubtsoff.bankagg.ui.common.ShotDropZone
import ru.maxrubtsoff.bankagg.ui.common.ShotThumbs
import ru.maxrubtsoff.bankagg.ui.common.rememberShotDragState
import ru.maxrubtsoff.bankagg.ui.common.shotDropTarget
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Several cashback screenshots at once: one card per bank (screens of one bank
 * become one list), the bank picked in a tap where the screen doesn't say it,
 * then "Сохранить всё".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashbackBatchScreen(
    vm: CashbackViewModel,
    ids: List<String>,
    onBack: () -> Unit,
    onOpenShot: (String) -> Unit,
    onEditGroup: (List<String>) -> Unit,
    onDone: () -> Unit
) {
    val shots by remember(ids) { vm.observeShots(ids) }.collectAsStateWithLifecycle(initialValue = null)
    val list = shots ?: emptyList()
    val processing = list.count { it.status == ShotStatus.PROCESSING }
    val byId = list.associateBy { it.id }

    // Cards rise in turn only after a reading seen here; coming back to a read batch shows it at once.
    var sawReading by remember { mutableStateOf(false) }
    LaunchedEffect(processing) { if (processing > 0) sawReading = true }

    var layoutText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(shots, processing) {
        val s = shots ?: return@LaunchedEffect
        if (processing > 0) return@LaunchedEffect
        val current = BatchLayout.decode(layoutText)
        // Re-grouped on every change: naming a bank joins that bank's card.
        val kept = if (current.isEmpty()) emptyList() else BatchLayout.reconcile(current, s.map { it.id })
        layoutText = BatchLayout.encode(joinSameBank(if (kept.isEmpty()) CashbackBatch.group(s.map(::itemOf)) else kept, s.associateBy { it.id }))
        if (s.isEmpty()) onDone()
    }
    val layout = BatchLayout.decode(layoutText).map { g -> g.filter { it in byId } }.filter { it.isNotEmpty() }
    val groups = layout.map { g -> CashbackBatch.merge(g.mapNotNull(byId::get).map(::itemOf)) }
    val ready = groups.filter { it.brand != null && it.savableLines.isNotEmpty() }

    // U-65: a thumbnail carried onto a bank's card goes there — the same move as «Перенести в…».
    val drag = rememberShotDragState { id, target ->
        val from = layout.indexOfFirst { id in it }
        val to = target?.let { key -> layout.indexOfFirst { it.first() == key } }
        val nothing = from < 0 || (target != null && (to == null || to < 0 || to == from)) || (target == null && layout[from].size == 1)
        if (!nothing) {
            val before = layoutText
            val oldBrand = byId[id]?.brand
            layoutText = BatchLayout.encode(BatchLayout.move(layout, id, to))
            // Joining a bank's card means it is that bank.
            val joined = to?.let { groups[it].brand }
            if (joined != null) vm.setShotsBrand(listOf(id), joined)
            vm.offerUndo("Скрин перенесён") {
                layoutText = before
                if (joined != null) vm.restoreShotBrand(id, oldBrand)
            }
        }
    }

    var moving by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Pair<CashbackBatch.Group, Int>?>(null) }
    val scope = rememberCoroutineScope()

    // Saves ready groups one by one; a bank that already has other offers this month asks first.
    fun saveFrom(queue: List<CashbackBatch.Group>) {
        val g = queue.firstOrNull() ?: run { saving = false; return }
        val brand = g.brand ?: return saveFrom(queue.drop(1))
        val lines = vm.draftLines(g.savableLines)
        scope.launch {
            when (vm.decideSave(g.monthKey, brand, lines)) {
                CashbackMerge.Decision.SAVE -> vm.commit(g.monthKey, brand, lines, CashbackViewModel.SaveMode.APPEND) {
                    vm.discardShots(g.ids); saveFrom(queue.drop(1))
                }
                CashbackMerge.Decision.ASK -> ask = g to vm.existingCountForBank(g.monthKey, brand)
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenHeader("Проверьте кэшбэк", onBack)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            // One place for the whole batch: the reading goes out as the cards come in (B-51, F-34).
            Crossfade(shots == null || processing > 0, animationSpec = Motion.tween(Motion.NORMAL), label = "batch") { waiting ->
                if (waiting) {
                    BatchProgress(done = list.size - processing, total = maxOf(list.size, ids.size))
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${RuPlural.screens(list.size)} → ${RuPlural.banks(groups.size)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slitok.Muted
                    )
                    groups.forEachIndexed { i, g ->
                        Box(Modifier.cascade(i, sawReading).shotDropTarget(drag, g.ids.first())) {
                            BankGroupCard(
                                group = g,
                                shots = g.ids.mapNotNull(byId::get),
                                onPickBank = { vm.setShotsBrand(g.ids, it) },
                                onOpenShot = { moving = it },
                                onRemoveShot = { vm.deleteShot(it) },
                                onFix = { onEditGroup(g.ids) },
                                drag = drag
                            )
                        }
                    }
                    ShotDropZone(drag, "Отдельный банк")
                    BrassButton(
                        onClick = { saving = true; saveFrom(ready) },
                        enabled = ready.isNotEmpty() && !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (ready.size == groups.size) "Сохранить всё" else "Сохранить готовые: ${ready.size}") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        ShotDragLayer(drag)
        }
    }

    moving?.let { id ->
        val from = layout.indexOfFirst { id in it }
        val others = groups.withIndex().filter { it.index != from }
        MoveShotSheet(
            path = byId[id]?.imagePath.orEmpty(),
            targets = others.map { it.value.brand?.title ?: "Банк не распознан" },
            newLabel = "Отдельный банк",
            onMove = { pick ->
                val target = pick?.let { others[it].index }
                layoutText = BatchLayout.encode(BatchLayout.move(layout, id, target))
                // Joining a bank's card means it is that bank.
                target?.let { groups[it].brand }?.let { vm.setShotsBrand(listOf(id), it) }
                moving = null
            },
            onRemove = { vm.deleteShot(id); moving = null },
            onDismiss = { moving = null }
        )
    }

    ask?.let { (g, count) ->
        val brand = g.brand ?: return@let
        fun answer(mode: CashbackViewModel.SaveMode) {
            ask = null
            val rest = ready.dropWhile { it.ids != g.ids }.drop(1)
            vm.commit(g.monthKey, brand, vm.draftLines(g.savableLines), mode) { vm.discardShots(g.ids); saveFrom(rest) }
        }
        AddOrReplaceDialog(
            bank = brand,
            existingCount = count,
            onAppend = { answer(CashbackViewModel.SaveMode.APPEND) },
            onReplace = { answer(CashbackViewModel.SaveMode.REPLACE) },
            onDismiss = { ask = null; saving = false }
        )
    }
}

private fun itemOf(s: CashbackShot) = CashbackBatch.Item(s.id, s.brand, s.monthKey, s.lines)

/** Cards of the same bank and month become one (after the user names a bank). */
private fun joinSameBank(groups: List<List<String>>, shots: Map<String, CashbackShot>): List<List<String>> {
    val out = linkedMapOf<Any, MutableList<String>>()
    groups.forEach { g ->
        val brand = g.firstNotNullOfOrNull { shots[it]?.brand }
        val key: Any = brand?.let { it to shots[g.first()]?.monthKey } ?: g.first()
        out.getOrPut(key) { mutableListOf() } += g
    }
    return out.values.toList()
}

@Composable
private fun BankGroupCard(
    group: CashbackBatch.Group,
    shots: List<CashbackShot>,
    onPickBank: (BankBrand) -> Unit,
    onOpenShot: (String) -> Unit,
    onRemoveShot: (String) -> Unit,
    onFix: () -> Unit,
    drag: ShotDragState
) {
    Panel(Modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val brand = group.brand
                if (brand != null) BankIcon(brand, size = 30.dp)
                else Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, Slitok.Faint, CircleShape), contentAlignment = Alignment.Center) {
                    Text("?", color = Slitok.Muted)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(brand?.title ?: "Какой это банк?", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        CashbackMonth.shortLabel(group.monthKey) + RuText.DOT + RuPlural.categories(group.savableLines.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Slitok.Muted
                    )
                }
            }
            if (group.brand == null) BankPickerRow(selected = null, onSelect = onPickBank)
            group.lines.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { l ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                l.text.ifBlank { "не прочитано" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (l.text.isBlank()) Slitok.Coral else Slitok.Fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                l.percent?.let(::formatPercent) ?: "?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Slitok.Brass2
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            ShotThumbs(shots.map { it.id to it.imagePath }, onOpenShot, drag, onRemove = onRemoveShot)
            PlateButton(onClick = onFix, modifier = Modifier.fillMaxWidth()) { Text("Исправить") }
        }
    }
}
