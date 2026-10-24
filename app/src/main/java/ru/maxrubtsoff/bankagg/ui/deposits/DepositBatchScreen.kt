package ru.maxrubtsoff.bankagg.ui.deposits

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BatchLayout
import ru.maxrubtsoff.bankagg.domain.model.DepositBatch
import ru.maxrubtsoff.bankagg.domain.model.DepositField
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.cashback.BankPickerRow
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BatchProgress
import ru.maxrubtsoff.bankagg.ui.common.MoveShotSheet
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.ShotThumbs
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate

/**
 * Several deposit screenshots at once: the app lays them out as deposits, the
 * user checks, moves a screenshot where it belongs, and saves them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositBatchScreen(
    vm: DepositsViewModel,
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
    // Laid out once recognition is over; afterwards only kept in sync with deletions.
    LaunchedEffect(shots, processing) {
        val s = shots ?: return@LaunchedEffect
        if (processing > 0) return@LaunchedEffect
        val present = s.map { it.id }
        val current = BatchLayout.decode(layoutText)
        layoutText = BatchLayout.encode(
            if (current.isEmpty()) DepositBatch.group(s.map(::itemOf)) else BatchLayout.reconcile(current, present)
        )
        if (s.isEmpty()) onDone()
    }
    val layout = BatchLayout.decode(layoutText).map { g -> g.filter { it in byId } }.filter { it.isNotEmpty() }
    val chosen = remember { mutableStateMapOf<String, Map<DepositBatch.Part, Any>>() }
    val groups = layout.map { g -> DepositBatch.merge(g.mapNotNull(byId::get).map(::itemOf), chosen[g.first()].orEmpty()) }
    var moving by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val ready = groups.filter { it.isSavable }

    Scaffold(
        topBar = {
            ScreenHeader("Проверьте вклады", onBack)
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
                    "${RuPlural.screens(list.size)} → ${RuPlural.deposits(groups.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slitok.Muted
                )
                groups.forEach { g ->
                    GroupCard(
                        group = g,
                        shots = g.ids.mapNotNull(byId::get),
                        onPickBank = { vm.setShotsBrand(g.ids, it) },
                        onChoose = { part, value -> chosen[g.ids.first()] = chosen[g.ids.first()].orEmpty() + (part to value) },
                        onOpenShot = { moving = it },
                        onFill = { vm.collapseGroup(g) { id -> onOpenShot(id) } }
                    )
                }
                BrassButton(
                    onClick = { saving = true; vm.saveGroups(ready) { saving = false } },
                    enabled = ready.isNotEmpty() && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (ready.size == groups.size) "Сохранить ${RuPlural.deposits(ready.size)}" else "Сохранить готовые: ${ready.size}") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    moving?.let { id ->
        val from = layout.indexOfFirst { id in it }
        val others = groups.withIndex().filter { it.index != from }
        MoveShotSheet(
            path = byId[id]?.imagePath.orEmpty(),
            targets = others.map { groupLabel(it.value) },
            newLabel = "Отдельный вклад",
            onMove = { pick ->
                layoutText = BatchLayout.encode(BatchLayout.move(layout, id, pick?.let { others[it].index }))
                moving = null
            },
            onRemove = { vm.deleteShot(id); moving = null },
            onDismiss = { moving = null }
        )
    }
}

private fun itemOf(s: DepositShot) = DepositBatch.Item(s.id, s.brand, s.draft)

private fun groupLabel(g: DepositBatch.Group): String =
    listOfNotNull(
        g.draft.title ?: g.brand?.title ?: "Банк не распознан",
        g.draft.principal?.let(::money) ?: g.draft.rateBp?.let(::ratePercent),
        g.draft.endDate?.let { "до ${formatDate(it)}" }
    ).joinToString(RuText.DOT)

@Composable
private fun GroupCard(
    group: DepositBatch.Group,
    shots: List<DepositShot>,
    onPickBank: (BankBrand) -> Unit,
    onChoose: (DepositBatch.Part, Any) -> Unit,
    onOpenShot: (String) -> Unit,
    onFill: () -> Unit
) {
    val d = group.draft
    Panel(Modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val brand = group.brand
                if (brand != null) BankIcon(brand, size = 30.dp)
                else Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, Slitok.Faint, CircleShape), contentAlignment = Alignment.Center) {
                    Text("?", color = Slitok.Muted)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    d.title ?: brand?.title ?: "Какой это банк?",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (group.brand == null) BankPickerRow(selected = null, onSelect = onPickBank)

            Fact("Сумма", d.principal?.let(::money))
            Fact("Ставка", group.rateBp?.let { ratePercent(it) + if (d.rateBp == null) " (по суммам)" else "" })
            Fact(
                "Срок",
                if (d.startDate != null || d.endDate != null) "${d.startDate?.let(::formatDate) ?: "?"} → ${d.endDate?.let(::formatDate) ?: "?"}" else null,
                check = DepositField.START in d.guessed || DepositField.END in d.guessed
            )
            d.exitAmount?.let { Fact("К выдаче", money(it)) }

            group.options.forEach { (part, values) -> OptionRow(part, values) { onChoose(part, it) } }

            ShotThumbs(shots.map { it.id to it.imagePath }, onOpenShot)
            if (!group.isSavable) {
                PlateButton(onClick = onFill, modifier = Modifier.fillMaxWidth()) { Text("Дополнить") }
            }
        }
    }
}

/** A recognised fact; a missing one says so, a worked-out one asks to be checked. */
@Composable
private fun Fact(label: String, value: String?, check: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, modifier = Modifier.width(84.dp))
        MoneyText(
            value ?: "не найдено",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) Slitok.Coral else Slitok.Fg,
            fontWeight = if (value == null) FontWeight.Normal else FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (check) Text("проверьте", style = MaterialTheme.typography.labelMedium, color = Slitok.Brass2)
    }
}

/** Screens disagree on a field: one chip per value. */
@Composable
private fun OptionRow(part: DepositBatch.Part, values: List<Any>, onPick: (Any) -> Unit) {
    val label = when (part) {
        DepositBatch.Part.PRINCIPAL -> "Сумма:"
        DepositBatch.Part.EXIT -> "К выдаче:"
        DepositBatch.Part.RATE -> "Ставка:"
        DepositBatch.Part.START -> "Начало:"
        DepositBatch.Part.END -> "Конец:"
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Brass2)
        values.forEach { v ->
            val text = when (v) {
                is Money -> money(v)
                is Int -> ratePercent(v)
                is LocalDate -> formatDate(v)
                else -> v.toString()
            }
            val shape = RoundedCornerShape(10.dp)
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = Slitok.Fg,
                modifier = Modifier.clip(shape).background(Slitok.Inset).border(1.dp, Slitok.Line, shape)
                    .clickable { onPick(v) }.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
