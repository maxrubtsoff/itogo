package ru.maxrubtsoff.bankagg.ui.cashback

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
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
import ru.maxrubtsoff.bankagg.ui.common.BatchProgress
import ru.maxrubtsoff.bankagg.ui.common.MoveShotSheet
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.ShotThumbs
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
    onDone: () -> Unit
) {
    val shots by remember(ids) { vm.observeShots(ids) }.collectAsStateWithLifecycle(initialValue = null)
    val list = shots ?: emptyList()
    val processing = list.count { it.status == ShotStatus.PROCESSING }
    val byId = list.associateBy { it.id }

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
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            if (shots == null || processing > 0) {
                BatchProgress(done = list.size - processing, total = maxOf(list.size, ids.size))
            } else {
                Text(
                    "${RuPlural.screens(list.size)} → ${RuPlural.banks(groups.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slitok.Muted
                )
                groups.forEach { g ->
                    BankGroupCard(
                        group = g,
                        shots = g.ids.mapNotNull(byId::get),
                        onPickBank = { vm.setShotsBrand(g.ids, it) },
                        onOpenShot = { moving = it },
                        onFix = { vm.collapseGroup(g) { id -> onOpenShot(id) } }
                    )
                }
                BrassButton(
                    onClick = { saving = true; saveFrom(ready) },
                    enabled = ready.isNotEmpty() && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (ready.size == groups.size) "Сохранить всё" else "Сохранить готовые: ${ready.size}") }
            }
            Spacer(Modifier.height(16.dp))
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
    onFix: () -> Unit
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
            ShotThumbs(shots.map { it.id to it.imagePath }, onOpenShot)
            PlateButton(onClick = onFix, modifier = Modifier.fillMaxWidth()) { Text("Исправить") }
        }
    }
}
