package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import ru.maxrubtsoff.bankagg.ui.common.rememberTabScrollState
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeaderGap
import androidx.compose.ui.platform.LocalContext
import ru.maxrubtsoff.bankagg.ui.common.LocalMyBanks
import ru.maxrubtsoff.bankagg.ui.common.BankStack
import ru.maxrubtsoff.bankagg.ui.common.rememberNotificationAccess
import ru.maxrubtsoff.bankagg.util.Perms
import ru.maxrubtsoff.bankagg.domain.model.EmptyStart
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.rememberCascade
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.SectionPager
import ru.maxrubtsoff.bankagg.ui.common.PagerTabs
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.common.sharedCard
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.deposits.DepositsSection
import ru.maxrubtsoff.bankagg.ui.deposits.DepositsViewModel
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The Capital tab: Счета · Вклады · Валюта · Наличные behind one switcher, also
 * swiped sideways. The route's [sectionArg] wins (rows on the overview open a
 * given section); otherwise the last opened section comes back.
 */
@Composable
fun CapitalScreen(
    vm: CapitalViewModel,
    depositsVm: DepositsViewModel,
    sectionArg: String?,
    parts: List<CapitalPart> = CapitalPart.entries,
    onOpenDeposit: (String) -> Unit,
    onOpenDepositShot: (String) -> Unit,
    onOpenDepositBatch: (List<String>) -> Unit,
    onManualDeposit: () -> Unit,
    onOpenPendingShots: () -> Unit,
    onOpenCurrency: (String) -> Unit,
    onOpenBanks: () -> Unit,
    selectedPackages: Set<String> = emptySet()
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val remembered by vm.lastSection.collectAsStateWithLifecycle()
    val pageOf = { p: CapitalPart? -> parts.indexOf(p).takeIf { it >= 0 } }
    val pager = rememberPagerState(initialPage = pageOf(CapitalPart.fromKey(sectionArg)) ?: pageOf(remembered) ?: 0) { parts.size }
    val scope = rememberCoroutineScope()
    // The route's section is applied once per back-stack entry: coming back from
    // a currency or a deposit must not undo a section the user switched to since.
    var appliedArg by rememberSaveable { mutableStateOf<String?>(null) }
    var settled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sectionArg, remembered) {
        val fromArg = CapitalPart.fromKey(sectionArg)
        val last = remembered
        when {
            fromArg != null && sectionArg != appliedArg -> {
                appliedArg = sectionArg; settled = true
                pageOf(fromArg)?.let { pager.scrollToPage(it) }
            }
            !settled && fromArg == null && last != null -> {
                settled = true
                pageOf(last)?.let { pager.scrollToPage(it) }
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { settled = true; vm.rememberSection(parts[it]) }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ScreenHeader("Капитал")
            Spacer(Modifier.height(ScreenHeaderGap))
            // One kept section needs no switch: the title already names the screen (B-24).
            if (parts.size > 1) {
                PagerTabs(parts.map { it.label }, pager) { scope.launch { pager.animateScrollToPage(it) } }
                Spacer(Modifier.height(12.dp))
            }

            val snap = snapshot
            if (snap == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Slitok.Brass) }
            } else {
                // Sections swipe sideways; each keeps its own vertical scroll.
                SectionPager(pager, Modifier.fillMaxSize()) { page ->
                    when (parts[page]) {
                        CapitalPart.ACCOUNTS -> AccountsSection(vm, snap, onOpenBanks, selectedPackages)
                        CapitalPart.DEPOSITS -> DepositsSection(depositsVm, onOpenDeposit, onOpenDepositShot, onOpenDepositBatch, onManualDeposit, onOpenPendingShots)
                        CapitalPart.CURRENCY -> CurrencySection(vm, snap, onOpenCurrency)
                        CapitalPart.CASH -> CashSection(vm, snap)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

// ---- Счета ----

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun AccountsSection(vm: CapitalViewModel, snap: CapitalSnapshot, onOpenBanks: () -> Unit, selectedPackages: Set<String>) {
    val byBank = snap.rubAccounts.groupBy { it.brand }
        .map { (brand, list) -> brand to list }
        .sortedByDescending { (_, list) -> list.sumOf { it.balance.minor } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberTabScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Panel(Modifier.fillMaxWidth().sharedCard("part-${CapitalPart.ACCOUNTS.key}"), radius = 22.dp) {
            Box(Modifier.fillMaxWidth().heroGlow(Slitok.Brass)) {
                Column(Modifier.padding(20.dp)) {
                    SectionHeroText("На счетах", Money.ofMinor(snap.part(CapitalPart.ACCOUNTS).valueMinor).format())
                }
            }
        }
        if (byBank.isEmpty()) {
            // Same rule as the empty Overview (B-23): never ask to pick banks that are picked.
            val myBanks = LocalMyBanks.current
            val collecting = myBanks.filter { b -> b.packages.any { it in selectedPackages } }
            val access by rememberNotificationAccess()
            val context = LocalContext.current
            val lead = EmptyStart.plan(Features(setOf(Feature.ACCOUNTS)), collecting.size, access).lead
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (lead) {
                        EmptyStart.Lead.PICK_BANKS, EmptyStart.Lead.NONE -> {
                            Text(
                                "Пока нет счетов. Выберите банки — балансы появятся из их уведомлений.".nbsp(),
                                style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted
                            )
                            BrassButton(onClick = onOpenBanks, modifier = Modifier.fillMaxWidth()) { Text("Выбрать банки") }
                        }
                        EmptyStart.Lead.GRANT_ACCESS -> {
                            BankStack(collecting)
                            Text("Нет доступа к уведомлениям", style = MaterialTheme.typography.titleMedium)
                            BrassButton(onClick = { Perms.openNotificationAccessSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Разрешить доступ")
                            }
                        }
                        EmptyStart.Lead.WAITING -> {
                            BankStack(collecting)
                            Text("Ждём уведомления", style = MaterialTheme.typography.titleMedium)
                            Text("Баланс появится после первой операции", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                        }
                    }
                }
            }
        } else {
            val play = rememberCascade("accounts")
            var editing by remember { mutableStateOf<Account?>(null) }
            editing?.let { a ->
                BalanceSheet(a, since = { vm.balanceSince(a) }, onSave = { vm.setBalance(a.id, it); editing = null }, onDismiss = { editing = null })
            }
            byBank.forEachIndexed { i, (brand, list) ->
                val allOut = list.all { it.excluded }
                Panel(Modifier.fillMaxWidth().cascade(i, play).tourTarget(TourTarget.ACCOUNT_ROW, active = i == 0, radius = 18.dp)) {
                    // Long press (U-18): leave the bank's accounts out of the capital, or bring them back.
                    LongPressMenu(items = listOf((if (allOut) "Учитывать в капитале" else "Не учитывать в капитале") to { vm.setExcluded(list.map { it.id }, !allOut) })) { onLong ->
                    val single = list.singleOrNull()
                    Row(
                        Modifier.fillMaxWidth()
                            // One account: a tap on the bank sets its balance (B-29).
                            .combinedClickable(onClick = { single?.let { editing = it } }, onLongClick = onLong)
                            .graphicsLayer { alpha = if (allOut) 0.45f else 1f }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BankIcon(brand, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(brand.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            single?.let { CheckedNote(it) }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (single?.needsBalance == true) {
                            BalanceAsk { editing = single }
                        } else {
                            MoneyText(
                                (if (list.any { it.estimated }) "≈\u00A0" else "") + Money.ofMinor(list.sumOf { it.balance.minor }).format(),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                maxLines = 1, softWrap = false
                            )
                        }
                    }
                    }
                    if (list.size > 1) {
                        list.forEach { a ->
                            HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                            LongPressMenu(items = listOf((if (a.excluded) "Учитывать в капитале" else "Не учитывать в капитале") to { vm.setExcluded(listOf(a.id), !a.excluded) })) { onLong ->
                                AccountLine(a, onLong) { editing = a }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun AccountLine(a: Account, onLongPress: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .graphicsLayer { alpha = if (a.excluded) 0.45f else 1f }
            .padding(start = 64.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                a.title.ifBlank { a.brand.title },
                style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            CheckedNote(a)
        }
        Spacer(Modifier.width(8.dp))
        if (a.needsBalance) BalanceAsk(onClick)
        else MoneyText(
            (if (a.estimated) "≈\u00A0" else "") + a.balance.format(a.currency),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false
        )
    }
}

/** «сверено 15 октября» under a counted balance; brass after a month — time to check it (B-29). */
@Composable
private fun CheckedNote(a: Account) {
    if (!a.estimated) return
    val at = a.checkedAt ?: return
    val stale = System.currentTimeMillis() - at > 30L * 24 * 60 * 60 * 1000
    Text(
        "сверено ${checkedDay(at)}",
        style = MaterialTheme.typography.bodySmall,
        color = if (stale) Slitok.Brass2 else Slitok.Muted,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

/** The bank never told the balance: one tap to give it (B-29). */
@Composable
private fun BalanceAsk(onClick: () -> Unit) {
    Text(
        "Укажите остаток",
        style = MaterialTheme.typography.labelLarge,
        color = Slitok.Brass2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Slitok.BrassPill)
            .border(1.dp, Slitok.BrassLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

// ---- Наличные ----

@Composable
private fun CashSection(vm: CapitalViewModel, snap: CapitalSnapshot) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val cash = snap.cash
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberTabScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.CASH_SUM, radius = 22.dp).sharedCard("part-${CapitalPart.CASH.key}").clickable { editing = true }, radius = 22.dp) {
            Box(Modifier.fillMaxWidth().heroGlow(Slitok.Sand)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeroText("Наличные", (cash?.amount ?: Money.ZERO).format())
                    if (cash == null) {
                        Text("сумма не задана", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                    }
                }
            }
        }
        BrassButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (cash == null) "Задать сумму" else "Изменить сумму")
        }
    }
    if (editing) {
        CashSheet(
            currentMinor = cash?.amount?.minor ?: 0L,
            onSave = { vm.setCash(it); editing = false },
            onDismiss = { editing = false }
        )
    }
}

/** "1 234,5" typed → kopecks; null for junk. Spaces of any kind are ignored. */
internal fun parseAmountMinor(text: String): Long? {
    val t = text.filterNot { it == ' ' || it == ' ' || it == ' ' }.replace(',', '.')
    if (t.isEmpty()) return null
    val parts = t.split('.')
    if (parts.size > 2 || parts[0].length > 13) return null
    val whole = parts[0].ifEmpty { "0" }
    val frac = parts.getOrNull(1).orEmpty()
    if (!whole.all { it.isDigit() } || !frac.all { it.isDigit() } || frac.length > 2) return null
    return whole.toLong() * 100 + frac.padEnd(2, '0').toLong()
}

/** Kopecks → editable text: "112000" / "1234,5". */
internal fun amountInput(minor: Long): String {
    val whole = minor / 100
    val frac = minor % 100
    return if (frac == 0L) "$whole" else "$whole," + "%02d".format(frac).trimEnd('0')
}
