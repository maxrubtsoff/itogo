package ru.maxrubtsoff.bankagg.ui.settings

import ru.maxrubtsoff.bankagg.ui.common.ButtonRow
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.MoreAction
import ru.maxrubtsoff.bankagg.ui.common.MoreMenu
import ru.maxrubtsoff.bankagg.ui.common.SwitchRow
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSummary
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * One bank (U-24): any bank opens here — its notifications switch and what it
 * holds (each line opens that section). Rare actions — the name and logo of a
 * bank of your own, «Убрать банк» — sit in «⋮» (U-43).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardScreen(
    vm: BanksViewModel,
    bankKey: String,
    features: Features,
    onBack: () -> Unit,
    onEdit: (BankBrand) -> Unit,
    onOpenCapital: (CapitalPart) -> Unit,
    onOpenCashback: () -> Unit
) {
    val mine by vm.mine.collectAsStateWithLifecycle()
    val collected by vm.collected.collectAsStateWithLifecycle()
    val bank = mine.firstOrNull { it.key == bankKey }
    val summary by remember(bankKey) { vm.summary(bankKey) }.collectAsStateWithLifecycle(initialValue = null)
    var asking by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ScreenHeader(
                "", onBack,
                actions = {
                    val b = bank ?: return@ScreenHeader
                    MoreMenu(
                        buildList {
                            // Known banks wear their app's icon; the rest can have letters or a picture.
                            if (!b.isKnown) add(MoreAction("Название и логотип", Icons.Filled.Edit) { onEdit(b) })
                            add(MoreAction("Убрать банк", Icons.Filled.Delete, danger = true) {
                                if (summary?.isEmpty != false) { vm.remove(b); onBack() } else asking = true
                            })
                        }
                    )
                }
            )
        }
    ) { padding ->
        if (bank == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BankIcon(bank, size = 84.dp)
                Text(bank.title, style = MaterialTheme.typography.headlineSmall)
            }

            if (features.collectsNotifications && bank.packages.isNotEmpty()) {
                val on = bank.packages.any { it in collected }
                Panel(Modifier.fillMaxWidth()) {
                    SwitchRow("Собирать уведомления", on, { vm.toggleCollect(bank) }) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = Slitok.Muted)
                    }
                }
            }

            summary?.let { s -> Holdings(s, features, onOpenCapital, onOpenCashback) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (asking && bank != null) {
        RemoveBankSheet(bank, onConfirm = { asking = false; vm.remove(bank); onBack() }, onDismiss = { asking = false })
    }
}

/** The bank's accounts, deposits and this month's cashback; a line opens its section. */
@Composable
private fun Holdings(s: BankSummary, features: Features, onOpenCapital: (CapitalPart) -> Unit, onOpenCashback: () -> Unit) {
    data class Line(val dot: Color, val label: String, val money: ru.maxrubtsoff.bankagg.domain.model.Money?, val note: String?, val open: () -> Unit)
    val lines = buildList {
        if (features.has(Feature.ACCOUNTS) && s.accountCount > 0)
            add(Line(Slitok.Brass, if (s.accountCount == 1) "Счёт" else "Счета", s.accounts, null) { onOpenCapital(CapitalPart.ACCOUNTS) })
        if (features.has(Feature.DEPOSITS) && s.depositCount > 0)
            add(Line(Slitok.Violet, if (s.depositCount == 1) "Вклад" else "Вклады · ${s.depositCount}", s.deposits, null) { onOpenCapital(CapitalPart.DEPOSITS) })
        if (features.has(Feature.CASHBACK) && s.cashbackCount > 0)
            add(Line(Slitok.Mint, "Кэшбэк · " + CashbackMonth.shortLabel(CashbackMonth.currentKey()).lowercase(), null, RuPlural.categories(s.cashbackCount), onOpenCashback))
    }
    if (lines.isEmpty()) return
    Panel(Modifier.fillMaxWidth()) {
        Column {
            lines.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 38.dp), color = Slitok.Line)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = l.open).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(l.dot))
                    Text(l.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (l.money != null) AmountText(l.money.format(), style = Amounts.Row, color = Slitok.Fg)
                    if (l.note != null) Text(l.note, style = MaterialTheme.typography.bodyLarge, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint)
                }
            }
        }
    }
}

/** «Убрать Т-Банк?» — its data hides, nothing is deleted (B-21). */
@Composable
fun RemoveBankSheet(bank: BankBrand, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SlitokSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BankIcon(bank, size = 48.dp)
            Text("Убрать ${bank.title}?", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Счета, вклады и кэшбэк банка скроются. Добавите банк снова — всё вернётся.",
            style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted
        )
        ButtonRow {
            PlateButton(onClick = onDismiss) { Text("Отмена") }
            DangerButton(onClick = onConfirm, filled = true) { Text("Убрать") }
        }
    }
}
