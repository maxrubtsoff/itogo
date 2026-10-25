@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.maxrubtsoff.bankagg.ui.overview

import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import androidx.compose.foundation.layout.ColumnScope
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.domain.model.HideToggle
import ru.maxrubtsoff.bankagg.ui.common.rememberTabScrollState
import ru.maxrubtsoff.bankagg.ui.common.GhostButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import ru.maxrubtsoff.bankagg.ui.common.LocalMyBanks
import ru.maxrubtsoff.bankagg.util.Perms
import ru.maxrubtsoff.bankagg.domain.model.EmptyStart
import ru.maxrubtsoff.bankagg.ui.common.BankStack
import androidx.compose.runtime.LaunchedEffect
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CapitalPartsToggle
import ru.maxrubtsoff.bankagg.ui.common.LocalAmountsHidden
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.grayscale
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.errorShake
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.common.rememberErrorShake
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalBar
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.capital.CapitalViewModel
import ru.maxrubtsoff.bankagg.ui.capital.PartDot
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.capital.color
import ru.maxrubtsoff.bankagg.ui.capital.gainColor
import ru.maxrubtsoff.bankagg.ui.capital.label
import ru.maxrubtsoff.bankagg.ui.capital.signedPct
import ru.maxrubtsoff.bankagg.ui.capital.signedRub
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackCard
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackViewModel
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.DeltaLine
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.sharedCard
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.common.NotificationAccessBanner
import ru.maxrubtsoff.bankagg.ui.common.rememberNotificationAccess
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Home: total capital from its four parts (each row opens that part in the
 * Capital tab), a composition bar, and the cashback card. ⚙ chooses which parts
 * count in the total.
 */
@Composable
fun OverviewScreen(
    vm: CapitalViewModel,
    cashbackVm: CashbackViewModel,
    onOpenCapital: (CapitalPart) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenCashback: () -> Unit,
    onOpenCashbackNext: () -> Unit,
    onOpenBanks: () -> Unit,
    onAddDeposit: () -> Unit,
    onRestore: () -> Unit = {},
    banksSelected: Boolean = false,
    selectedPackages: Set<String> = emptySet(),
    features: Features = Features.ALL,
    newBanks: List<BankCandidate> = emptyList(),
    onOpenNewBanks: () -> Unit = {},
    onToggleHidden: () -> Unit = {}
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val glance = remember(days, snapshot?.today) { snapshot?.let { CapitalHistory.glance(days, it.today) } }
    val notifAccess by rememberNotificationAccess()
    var tuning by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberTabScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("Обзор") {
            val hidden = LocalAmountsHidden.current
            val hasAmounts = snapshot?.parts?.any { it.hasData } == true
            if (HideToggle.visible(hasAmounts, hidden)) IconButton(onClick = onToggleHidden, modifier = Modifier.tourTarget(TourTarget.HIDE_EYE, radius = 24.dp)) {
                Icon(
                    if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (hidden) "Показать суммы" else "Скрыть суммы",
                    tint = if (hidden) Slitok.Brass2 else Slitok.Muted
                )
            }
        }
        // Android sometimes revokes the access; then balances silently stop — say it here.
        // The banks that collect notifications: the empty card shows them waiting (B-23).
        val myBanks = LocalMyBanks.current
        val collecting = remember(myBanks, selectedPackages) { myBanks.filter { b -> b.packages.any { it in selectedPackages } } }
        val emptyCapital = snapshot?.parts?.none { it.hasData } == true
        // An empty card already asks for access itself.
        if (features.showNotifAccessOnOverview && banksSelected && !notifAccess && !emptyCapital) NotificationAccessBanner()
        if (features.showNewBanksPrompt && newBanks.isNotEmpty()) NewBanksBanner(newBanks, onOpenNewBanks)

        val snap = snapshot
        if (features.showCapital) when {
            snap == null -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator(color = Slitok.Brass)
            }
            snap.parts.none { it.hasData } -> EmptyCapital(
                plan = EmptyStart.plan(features, collecting.size, notifAccess),
                collecting = collecting,
                onOpenBanks = onOpenBanks,
                onAddDeposit = onAddDeposit,
                onAddCurrency = { onOpenCapital(CapitalPart.CURRENCY) },
                onSetCash = { onOpenCapital(CapitalPart.CASH) },
                onRestore = onRestore
            )
            else -> CapitalHero(
                snap,
                onTune = { tuning = true },
                onOpenPart = onOpenCapital,
                glance = glance,
                onOpenHistory = onOpenHistory,
                onToggleIncluded = { part -> vm.setPartIncluded(part.part, !part.included) },
                onToggleHidden = onToggleHidden
            )
        }

        if (features.showCashback) CashbackCard(cashbackVm, onOpen = onOpenCashback, onOpenNextMonth = onOpenCashbackNext)
        Spacer(Modifier.height(8.dp))
    }

    if (tuning) {
        val parts = snapshot?.parts.orEmpty()
        SlitokSheet(onDismiss = { tuning = false }) {
            // Only the sections the user keeps (F-21).
            val kept = CapitalPart.entries.filter { it in features.capitalParts }
            val included = kept.filter { p -> parts.firstOrNull { it.part == p }?.included ?: true }.toSet()
            Row(verticalAlignment = Alignment.Bottom) {
                Text("В общий капитал", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, modifier = Modifier.weight(1f))
                Text("${included.size} из ${kept.size}", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            }
            kept.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { p ->
                        val value = parts.firstOrNull { it.part == p }?.valueMinor ?: 0L
                        PartTile(p, p in included, value, Modifier.weight(1f)) {
                            val next = CapitalPartsToggle.toggle(included, p)
                            if (next != included) vm.setPartIncluded(p, p in next) else it()
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** «Новые банки · 2» with their icons stacked; a tap opens the list (F-23). */
@Composable
private fun NewBanksBanner(list: List<BankCandidate>, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Slitok.BrassTint, Slitok.PanelTop)))
            .border(1.dp, Slitok.BrassLine, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            list.take(3).forEach { c -> BankIcon(BankBrand("app:${c.packageName}", c.appName, 0xFF4A5568), size = 36.dp) }
        }
        Column(Modifier.weight(1f)) {
            Text("Новые банки · ${list.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(list.joinToString(", ") { it.appName }, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Brass2)
    }
}

/**
 * A part as a tile (F-12): its picture, name and sum. On — tinted in its colour
 * with a glow; off — grey and a touch smaller. [onClick] gets a "shake" to call
 * when the tap is refused (the last part can't be switched off).
 */
@Composable
private fun PartTile(part: CapitalPart, on: Boolean, valueMinor: Long, modifier: Modifier, onClick: (shake: () -> Unit) -> Unit) {
    val color = part.color()
    val shape = RoundedCornerShape(20.dp)
    val shake = rememberErrorShake()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val artScale by animateFloatAsState(if (on) 1f else 0.9f, label = "art")
    val artAlpha by animateFloatAsState(if (on) 1f else 0.5f, label = "alpha")
    val border by animateColorAsState(if (on) color else Slitok.Line, label = "border")
    Column(
        modifier
            .errorShake(shake)
            .pressScale()
            .clip(shape)
            .background(
                if (on) Brush.linearGradient(listOf(color.copy(alpha = 0.16f), Slitok.PanelTop)) else Brush.linearGradient(listOf(Slitok.PanelBottom, Slitok.PanelBottom))
            )
            .border(1.5.dp, border, shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick {
                    scope.launch { shake.play() }
                }
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CapitalPartArt(
            part,
            Modifier
                .height(64.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = artScale; scaleY = artScale; alpha = artAlpha }
                .then(if (on) Modifier else Modifier.grayscale())
        )
        Text(part.label, style = MaterialTheme.typography.titleSmall, color = if (on) Slitok.Fg else Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AmountText(
            Money.ofMinor(valueMinor).format(),
            style = MaterialTheme.typography.bodySmall,
            color = Slitok.Muted,
            minSize = 10.sp
        )
    }
}

@Composable
private fun CapitalHero(
    snap: CapitalSnapshot,
    onTune: () -> Unit,
    onOpenPart: (CapitalPart) -> Unit,
    glance: CapitalHistory.Glance?,
    onOpenHistory: () -> Unit = {},
    onToggleIncluded: (CapitalSnapshot.Part) -> Unit,
    onToggleHidden: () -> Unit
) {
    val shown = snap.parts.filter { it.hasData }
    Panel(Modifier.fillMaxWidth(), radius = 22.dp) {
        Box(Modifier.fillMaxWidth().heroGlow()) {
            Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Общий капитал", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, modifier = Modifier.weight(1f))
                    IconButton(onClick = onTune) {
                        Icon(Icons.Filled.Tune, contentDescription = "Что входит в капитал", tint = Slitok.Muted)
                    }
                }
                // U-45: no long press on the total — the eye above already hides the amounts.
                run {
                    // A new total rolls in from below (or above, when it fell) instead of jumping.
                    androidx.compose.animation.AnimatedContent(
                        targetState = snap.totalMinor,
                        transitionSpec = {
                            val up = targetState > initialState
                            (androidx.compose.animation.slideInVertically { if (up) it / 2 else -it / 2 } + androidx.compose.animation.fadeIn())
                                .togetherWith(androidx.compose.animation.slideOutVertically { if (up) -it / 2 else it / 2 } + androidx.compose.animation.fadeOut())
                        },
                        label = "total"
                    ) { total ->
                        AmountText(
                            Money.ofMinor(total).format(),
                            style = MaterialTheme.typography.displaySmall,
                            minSize = 20.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
                // F-36: the month's change under the sum; a tap opens the history.
                glance?.let {
                    DeltaLine(Money.ofMinor(it.change.deltaMinor), "за месяц", it.spark, Modifier.padding(end = 12.dp), onClick = onOpenHistory)
                }
                Spacer(Modifier.height(if (glance == null) 14.dp else 6.dp))
                CompositionBar(snap, Modifier.padding(end = 12.dp))
                Spacer(Modifier.height(6.dp))
                shown.forEachIndexed { i, part ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 22.dp, end = 12.dp), color = Slitok.Line)
                    PartRow(snap, part, onLongToggle = { onToggleIncluded(part) }) { onOpenPart(part.part) }
                }
            }
        }
    }
}

/**
 * Segments proportional to the included parts, in their colours: one capsule,
 * parts butt against each other (U-21); a tiny part keeps at least 12 dp so it
 * reads as a piece of the bar, not a dot.
 */
@Composable
private fun CompositionBar(snap: CapitalSnapshot, modifier: Modifier = Modifier) {
    val segments = snap.parts.filter { it.share > 0f }
    // The bar grows in once per launch, not on every return to the Overview (U-29).
    val grow = remember { Animatable(if (BarIntro.pending()) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.tween(Motion.SLOW)) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minShare = (12.dp / maxWidth).coerceAtLeast(CapitalBar.MIN_SHARE)
        val widths = remember(segments, minShare) { CapitalBar.widths(segments.map { it.share }, minShare) }
        Row(
            Modifier.fillMaxWidth(grow.value.coerceAtLeast(0.001f)).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Slitok.Inset)
        ) {
            segments.forEachIndexed { i, s ->
                Box(Modifier.weight(widths[i]).fillMaxHeight().background(s.part.color()))
            }
        }
    }
}

/** Whether the capital bar still has to grow in during this process (U-29). */
private object BarIntro {
    private var played = !Motion.enabled
    fun pending(): Boolean = (!played).also { played = true }
}

@Composable
private fun PartRow(snap: CapitalSnapshot, part: CapitalSnapshot.Part, onLongToggle: () -> Unit, onClick: () -> Unit) {
    // Long press: count it in the total or not, without the sheet (U-7).
    LongPressMenu(
        items = listOf((if (part.included) "Не учитывать в общем капитале" else "Учитывать в общем капитале") to onLongToggle)
    ) { onLongPress ->
    Row(
        Modifier
            .fillMaxWidth()
            // U-32: the row grows into its section of «Капитал» and shrinks back on return.
            .sharedCard("part-${part.part.key}")
            .pressScale()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PartDot(part.part, part.included)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    part.part.label, style = MaterialTheme.typography.bodyLarge,
                    color = if (part.included) Slitok.Fg else Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                AmountText(
                    Money.ofMinor(part.valueMinor).format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (part.included) Slitok.Fg else Slitok.Faint,
                    minSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            secondLine(snap, part)?.let { (text, color) ->
                MoneyText(text, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint)
    }
    }
}

/** The small line under a part: what grew, or why it doesn't count. */
private fun secondLine(snap: CapitalSnapshot, part: CapitalSnapshot.Part): Pair<String, androidx.compose.ui.graphics.Color>? {
    if (!part.included) return "не в итоге" to Slitok.Faint
    return when (part.part) {
        CapitalPart.DEPOSITS -> snap.depositsAccruedMinor.takeIf { it != 0L }
            ?.let { "${signedRub(it)} накоплено" to Slitok.Mint }
        CapitalPart.CURRENCY -> {
            val c = snap.currency
            when {
                c.missingRates.isNotEmpty() -> "нет курса для ${c.missingRates.joinToString(", ")} — ↻" to Slitok.Brass2
                c.costMinor > 0L -> ("наличные ${signedRub(c.unrealizedMinor)}" +
                    (c.unrealizedPct?.let { RuText.DOT + signedPct(it) } ?: "")) to gainColor(c.unrealizedMinor)
                else -> null
            }
        }
        CapitalPart.CASH, CapitalPart.ACCOUNTS -> null
    }
}

@Composable
private fun EmptyCapital(
    plan: EmptyStart.Plan,
    collecting: List<BankBrand>,
    onOpenBanks: () -> Unit,
    onAddDeposit: () -> Unit,
    onAddCurrency: () -> Unit,
    onSetCash: () -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current
    // F-30: plates with the parts' 3D pictures instead of five alike dark buttons.
    // The lead plate (accounts from notifications) stands out with a brass edge.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (plan.lead != EmptyStart.Lead.NONE) {
            StartPlate(lead = true, art = { CapitalPartArt(CapitalPart.ACCOUNTS, Modifier.size(width = 72.dp, height = 56.dp)) }) {
                // B-54: the title takes the whole line (it wraps by words, never into a column); the banks stand under it.
                Text(
                    when (plan.lead) {
                        EmptyStart.Lead.WAITING -> "Ждём уведомления"
                        EmptyStart.Lead.GRANT_ACCESS -> "Счета из уведомлений"
                        else -> "Счета из уведомлений"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (collecting.isNotEmpty()) BankStack(collecting)
                when (plan.lead) {
                    EmptyStart.Lead.WAITING ->
                        Text("Баланс появится после первой операции", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                    EmptyStart.Lead.GRANT_ACCESS ->
                        BrassButton(onClick = { Perms.openNotificationAccessSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ") }
                    else ->
                        BrassButton(onClick = onOpenBanks, modifier = Modifier.fillMaxWidth()) { Text("Выбрать банки") }
                }
            }
        }
        val manual = buildList<Triple<CapitalPart, String, () -> Unit>> {
            if (plan.addDeposit) add(Triple(CapitalPart.DEPOSITS, "Вклад", onAddDeposit))
            if (plan.addCurrency) add(Triple(CapitalPart.CURRENCY, "Валюта", onAddCurrency))
            if (plan.setCash) add(Triple(CapitalPart.CASH, "Наличные", onSetCash))
        }
        manual.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (part, title, open) ->
                    StartPlate(lead = false, modifier = Modifier.weight(1f), onClick = open, art = {
                        CapitalPartArt(part, Modifier.size(width = 64.dp, height = 50.dp))
                    }) { Text(title, style = MaterialTheme.typography.titleMedium) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // After a reinstall: everything comes back from a copy in one go.
        GhostButton("Восстановить из копии", onClick = onRestore, modifier = Modifier.fillMaxWidth())
    }
}

/** One plate of the empty Overview: a 3D picture, a title, and what to do. */
@Composable
private fun StartPlate(
    lead: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    art: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .pressScale()
            .clip(shape)
            .background(
                if (lead) Brush.linearGradient(listOf(Slitok.Brass.copy(alpha = 0.14f), Slitok.PanelTop))
                else Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.PanelBottom))
            )
            .border(1.5.dp, if (lead) Slitok.BrassLine else Slitok.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(if (lead) 18.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        art()
        content()
    }
}

