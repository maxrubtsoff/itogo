@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.maxrubtsoff.bankagg.ui.deposits

import ru.maxrubtsoff.bankagg.domain.model.TourSample
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import androidx.compose.material.icons.filled.Lightbulb
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.ui.onboarding.LocalTourOffer
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.ui.overview.CapitalPartArt
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.rememberTabScrollState
import ru.maxrubtsoff.bankagg.ui.common.reorderable
import androidx.compose.runtime.key
import androidx.compose.ui.layout.LookaheadScope
import ru.maxrubtsoff.bankagg.ui.common.sharedCard
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.rememberCascade
import ru.maxrubtsoff.bankagg.ui.common.RollingAmount
import ru.maxrubtsoff.bankagg.ui.common.SectionActions
import ru.maxrubtsoff.bankagg.ui.common.AddSheet
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import androidx.compose.foundation.combinedClickable
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.DepositOrder
import ru.maxrubtsoff.bankagg.ui.common.SortControl
import ru.maxrubtsoff.bankagg.ui.common.SortSheet
import ru.maxrubtsoff.bankagg.domain.model.DepositSort
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.domain.model.DepositGroups
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PendingShotsPlate
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.time.LocalDate

/**
 * The deposits section of the Capital tab: one big figure, a compact list in
 * the order the user picked, finished deposits folded below.
 */
@Composable
fun DepositsSection(
    vm: DepositsViewModel,
    onOpenDeposit: (String) -> Unit,
    onOpenShot: (String) -> Unit,
    onOpenBatch: (List<String>) -> Unit,
    onManual: () -> Unit,
    /** Under the hero sum: the month's change (F-36). */
    heroExtra: @Composable () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.shots.collectAsStateWithLifecycle()
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    // "+" asks only "from screenshots or by hand"; screenshots go straight to the gallery.
    val pickImages = rememberDepositShotsPicker(vm, onOpenShot, onOpenBatch)
    val onAdd = { adding = true }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberTabScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionActions(
            summary = if (state.isEmpty) "Вкладов пока нет" else RuPlural.deposits(state.active.size),
            controls = {
                if (state.active.size > 1) {
                    SortControl(
                        label = sortTitle(state.order.sort).lowercase(),
                        reversed = state.order.reversed,
                        directionLabel = orderLabel(state.order),
                        onFlip = vm::flipSort,
                        onOpen = { sorting = true }
                    )
                }
            },
            onAdd = onAdd,
            addDescription = "Добавить вклад"
        )
        if (pending.isNotEmpty()) {
            PendingShotsPlate(
                shots = pending.size,
                thumbs = pending.map { it.imagePath },
                ready = pending.count { it.status == ShotStatus.RECOGNIZED },
                check = pending.count { it.status == ShotStatus.NEEDS_INPUT },
                unread = pending.count { it.status == ShotStatus.FAILED },
                onOpen = { onOpenBatch(pending.map { it.id }) }
            )
        }
        if (state.isEmpty) {
            EmptyState(onAdd, LocalTourOffer.current(TourSection.DEPOSITS, false))
        } else {
            HeroSummary(state.summary, heroExtra)
            if (state.active.isNotEmpty()) {
                if (state.order.sort == DepositSort.BANK) {
                    val groups = remember(state.active, state.order) {
                        DepositGroups.byBank(state.active, { it.deposit }, { it.projection }, state.order.reversed)
                    }
                    val play = rememberCascade("deposit-groups")
                    groups.forEachIndexed { i, g -> Box(Modifier.cascade(i, play)) { BankGroup(g, state.today, onOpenDeposit) } }
                } else {
                    DepositList(state.active, state.today, dimmed = false, onOpenDeposit)
                }
            }
        }
        if (state.completed.isNotEmpty()) {
            CompletedHeader(
                count = state.completedTotals.count,
                income = state.completedTotals.income,
                expanded = showCompleted,
                onToggle = { showCompleted = !showCompleted }
            )
            AnimatedVisibility(showCompleted) {
                DepositList(state.completed, state.today, dimmed = true, onOpenDeposit)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (adding) {
        AddSheet(
            title = "Добавить вклад",
            onShots = pickImages,
            onManual = onManual,
            onDismiss = { adding = false }
        )
    }

    if (sorting) {
        SortSheet(
            options = DepositSort.entries,
            order = state.order,
            title = ::sortTitle,
            direction = ::orderLabel,
            onPick = { vm.pickSort(it); sorting = false },
            onDismiss = { sorting = false }
        )
    }
}

/**
 * The total now and today's gain (U-36): one figure up front. What went in and
 * what comes out — one quiet line under it, short figures, text colour (B-34).
 */
@Composable
private fun HeroSummary(summary: DepositMath.Summary, extra: @Composable () -> Unit = {}) {
    Panel(modifier = Modifier.fillMaxWidth(), radius = 22.dp) {
        Box(Modifier.fillMaxWidth().heroGlow(Slitok.Violet)) {
            Column(Modifier.padding(20.dp)) {
                RollingAmount(summary.currentValue.minor) { v ->
                    AmountText(money(Money.ofMinor(v)), style = Amounts.Hero, color = Slitok.Fg, minSize = 20.sp)
                }
                Spacer(Modifier.height(6.dp))
                AmountText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Slitok.Mint, fontWeight = FontWeight.SemiBold)) {
                            append(signedMoney(summary.dailyAccrual))
                        }
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize, color = Slitok.Muted)) {
                            append(" сегодня")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slitok.Muted
                )
                extra()
                Spacer(Modifier.height(10.dp))
                MoneyText(
                    "вложено ${MoneyCompact.format(summary.principal)}${RuText.DOT}к выдаче ${MoneyCompact.format(summary.payout)}".nbsp(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slitok.Muted
                )
            }
        }
    }
}

// ---- sorting ----

/** The chip text: where the list starts. */
private fun orderLabel(order: DepositOrder): String {
    val r = order.reversed
    return when (order.sort) {
        DepositSort.END -> if (r) "дальние" else "ближайшие"
        DepositSort.START -> if (r) "старые" else "новые"
        DepositSort.AMOUNT -> if (r) "мелкие" else "крупные"
        DepositSort.GAIN -> if (r) "меньше прирост" else "больше прирост"
        DepositSort.RATE -> if (r) "ниже ставка" else "выше ставка"
        DepositSort.INCOME -> if (r) "меньше доход" else "больше доход"
        DepositSort.BANK -> if (r) "банки: меньше сначала" else "банки: больше сначала"
    }
}

private fun sortTitle(sort: DepositSort): String = when (sort) {
    DepositSort.END -> "По закрытию"
    DepositSort.START -> "По открытию"
    DepositSort.AMOUNT -> "По сумме"
    DepositSort.GAIN -> "По приросту"
    DepositSort.RATE -> "По ставке"
    DepositSort.INCOME -> "По доходу за срок"
    DepositSort.BANK -> "По банкам"
}

// ---- list ----

/** [withBank]: a list not grouped by bank shows each deposit's bank logo. */
@Composable
private fun DepositList(
    items: List<DepositsViewModel.Item>,
    today: LocalDate,
    dimmed: Boolean,
    onOpenDeposit: (String) -> Unit,
    withBank: Boolean = true
) {
    // Rows appear one by one the first time the deposits are shown (U-17).
    val play = rememberCascade(if (dimmed) "deposits-done" else "deposits")
    Panel(Modifier.fillMaxWidth().alpha(if (dimmed) 0.6f else 1f), radius = 20.dp) {
        // A new sort order moves the rows to their places (U-27).
        LookaheadScope {
        Column {
            items.forEachIndexed { i, item ->
                val id = item.deposit.id
                key(id) {
                Column(Modifier.reorderable(this@LookaheadScope)) {
                if (i > 0) HorizontalDivider(Modifier.padding(start = if (withBank) 62.dp else 16.dp), color = Slitok.Line)
                // U-45: no long-press menu — changing, topping up and deleting live in the deposit's card.
                Box(Modifier.cascade(i, play).tourTarget(TourTarget.LIST_ROW, active = id == TourSample.HOLIDAY_ID, radius = 16.dp)) { DepositRow(item, today, withBank) { onOpenDeposit(id) } }
                }
                }
            }
        }
        }
    }
}

/** A bank's header — logo, name and how much is there — over its deposits. */
@Composable
private fun BankGroup(
    group: DepositGroups.BankGroup<DepositsViewModel.Item>,
    today: LocalDate,
    onOpenDeposit: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            BankIcon(group.brand, size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                group.brand.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            MoneyText(money(group.value), style = Amounts.Small, color = Slitok.Fg, maxLines = 1)
        }
        DepositList(group.items, today, dimmed = false, onOpenDeposit, withBank = false)
    }
}

/**
 * One deposit (U-36): its name and value; how long is left and the income so
 * far; a thin term bar. The rate lives in the card.
 */
@Composable
private fun DepositRow(item: DepositsViewModel.Item, today: LocalDate, withBank: Boolean, onClick: () -> Unit) {
    val deposit = item.deposit
    val projection = item.projection
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (withBank) {
            BankIcon(deposit.brand, size = 36.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    depositName(deposit),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slitok.Fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                MoneyText(money(projection.currentValue), style = Amounts.Row, color = Slitok.Fg, maxLines = 1, softWrap = false)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.upcoming) "с ${shortDate(deposit.startDate)}" else remainingShort(deposit.endDate, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.upcoming) Slitok.Brass2 else Slitok.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                MoneyText(signedMoney(projection.accruedIncome), style = Amounts.Small, color = Slitok.Mint, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(2.dp))
            ProgressBar(projection.progress.toFloat(), height = 3.dp)
        }
    }
}

/** «ещё 9 дн.» — or «завершён», «сегодня». */
private fun remainingShort(endDate: LocalDate, today: LocalDate): String =
    daysLeftShort(endDate, today).let { if (it.endsWith("дн.")) "ещё $it" else it }

@Composable
private fun EmptyState(onAdd: () -> Unit, onTour: (() -> Unit)?) {
    ru.maxrubtsoff.bankagg.ui.common.EmptyState(
        title = "Пока нет вкладов",
        art = { CapitalPartArt(CapitalPart.DEPOSITS, Modifier.size(width = 84.dp, height = 64.dp)) }
    ) {
        BrassButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Добавить вклад")
        }
        if (onTour != null) PlateButton(onClick = onTour, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null)
            Text("Как это работает")
        }
    }
}

/** «Завершённые · N · доход +X ₽» — a collapsed group of finished deposits. */
@Composable
private fun CompletedHeader(count: Int, income: Money, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AmountText(
            "Завершённые${RuText.DOT}$count${RuText.DOT}доход ${signedMoney(income)}",
            style = MaterialTheme.typography.titleMedium,
            color = Slitok.Muted,
            minSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Свернуть" else "Показать",
            tint = Slitok.Muted
        )
    }
}

@Composable
fun ProgressBar(fraction: Float, height: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(Slitok.Line)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(Slitok.Violet, Slitok.VioletLight)))
        )
    }
}
