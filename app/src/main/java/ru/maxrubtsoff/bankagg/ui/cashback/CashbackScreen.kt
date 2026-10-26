@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.maxrubtsoff.bankagg.ui.cashback

import ru.maxrubtsoff.bankagg.domain.model.TourSample
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import androidx.compose.material.icons.filled.Lightbulb
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.ui.onboarding.LocalTourOffer
import ru.maxrubtsoff.bankagg.ui.common.ValueChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import ru.maxrubtsoff.bankagg.ui.common.rememberTabScrollState
import androidx.compose.runtime.key
import ru.maxrubtsoff.bankagg.ui.common.CLOSE_MS
import ru.maxrubtsoff.bankagg.ui.common.reorderable
import androidx.compose.ui.layout.LookaheadScope
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.rememberCascade
import ru.maxrubtsoff.bankagg.ui.common.SectionActions
import ru.maxrubtsoff.bankagg.ui.common.AddSheet
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SectionPager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableIntStateOf
import ru.maxrubtsoff.bankagg.ui.common.PagerTabs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.domain.model.SortOrder
import ru.maxrubtsoff.bankagg.ui.common.SortControl
import ru.maxrubtsoff.bankagg.ui.common.SortSheet
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.ui.common.ShotImage
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.CashbackSpotlight
import ru.maxrubtsoff.bankagg.domain.model.CashbackSummary
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.CategoryIcon
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PendingShotsPlate
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File


/**
 * The cashback of a month (U-47), a page of «Платежи»: the month, the view
 * (by bank / by category) and the «+» are in the section's row of actions (U-58).
 */
@Composable
fun CashbackScreen(
    vm: CashbackViewModel,
    monthOffset: Int,
    byCategory: Boolean,
    onMonthOffset: (Int) -> Unit,
    onOpenShot: (String) -> Unit,
    onOpenBatch: (List<String>) -> Unit,
    onEditBank: (monthKey: String, BankBrand) -> Unit,
    onAddManual: (monthKey: String) -> Unit = {},
    onByCategory: (Boolean) -> Unit = {}
) {
    val anchor = remember { CashbackMonth.currentKey() }
    val currentKey = CashbackMonth.keyForOffset(anchor, monthOffset)
    val isCurrentMonth = currentKey == CashbackMonth.currentKey()
    val hintNextMonth = remember { CashbackMonth.shouldOfferNextMonth() }

    var adding by remember { mutableStateOf(false) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    // The month in view is read once: the line under the actions and the page share it.
    // null — not loaded yet (no «Кэшбэка пока нет» flash); keyed, so a stale month never shows.
    val loaded by remember(currentKey) { vm.monthItems(currentKey).map { currentKey to it } }
        .collectAsStateWithLifecycle(initialValue = null)
    val monthItems = loaded?.takeIf { it.first == currentKey }?.second

    // Photo picker — adds a shot to the month in view, then opens it for review.
    // One screenshot opens for review; several go to the batch check.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ru.maxrubtsoff.bankagg.ui.deposits.MAX_PICK)
    ) { uris: List<Uri> ->
        when (uris.size) {
            0 -> Unit
            1 -> vm.addShot(uris.first(), currentKey) { shotId -> onOpenShot(shotId) }
            else -> vm.addShots(uris, currentKey, onOpenBatch)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionActions(
            summary = remember(monthItems) { monthItems?.let { CashbackSummary.caption(it) }.orEmpty() },
            lead = { MonthChip(CashbackMonth.shortLabel(currentKey)) { pickingMonth = true } },
            controls = { ViewToggle(byCategory, onByCategory) },
            onAdd = { adding = true },
            addDescription = "Добавить кэшбэк"
        )
        // U-62: the hint fades and folds away instead of vanishing; the label is kept for the exit.
        val hintLabel = remember(currentKey) { CashbackMonth.shortLabel(CashbackMonth.nextKey(currentKey)) }
        AnimatedVisibility(
            visible = isCurrentMonth && hintNextMonth,
            enter = fadeIn(Motion.tween(Motion.NORMAL)) + expandVertically(Motion.tween(Motion.NORMAL, Motion.Standard)),
            exit = fadeOut(Motion.tween(Motion.FAST)) + shrinkVertically(Motion.tween(Motion.FAST, Motion.Standard))
        ) {
            NextMonthHint(
                nextLabel = hintLabel,
                onClick = { onMonthOffset(monthOffset + 1) }
            )
        }
        // The next month slides in from the side it lies on.
        // contentKey: only the month moves the page; new items of the same month just recompose it.
        AnimatedContent(
            targetState = MonthData(currentKey, monthItems),
            contentKey = { it.key },
            transitionSpec = {
                val dir = if (targetState.key > initialState.key) 1 else -1
                (slideInHorizontally(Motion.tween(Motion.NORMAL, Motion.Standard)) { w -> dir * w / 5 } + fadeIn(Motion.tween(Motion.NORMAL))) togetherWith
                    (slideOutHorizontally(Motion.tween(Motion.FAST, Motion.Standard)) { w -> -dir * w / 8 } + fadeOut(Motion.tween(Motion.FAST)))
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "month"
        ) { month ->
            val monthKey = month.key
            MonthPage(
                vm = vm,
                monthKey = monthKey,
                items = month.items,
                byCategory = byCategory,
                onEditBank = { brand -> onEditBank(monthKey, brand) },
                onOpenBatch = onOpenBatch
            )
        }
    }

    if (pickingMonth) {
        MonthSheet(anchor, monthOffset, onPick = { onMonthOffset(it); pickingMonth = false }, onDismiss = { pickingMonth = false })
    }
    if (adding) {
        AddSheet(
            title = "Добавить кэшбэк",
            onShots = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onManual = { onAddManual(currentKey) },
            onDismiss = { adding = false }
        )
    }
}

/** A month page's state: its key and the items (null until loaded). */
private data class MonthData(val key: String, val items: List<CashbackItem>?)

@Composable
private fun NextMonthHint(nextLabel: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Slitok.BrassTint)
            .border(1.dp, Slitok.BrassLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$nextLabel уже близко — можно добавить кэшбэк заранее",
            style = MaterialTheme.typography.bodyMedium,
            color = Slitok.Brass2,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Brass2)
    }
}


/**
 * «По категориям»: one line per category — the bank(s) with the best percent and
 * that percent. A tap unfolds every bank's offer under it: logo, what the bank
 * calls it, its percent. A category with one bank doesn't unfold.
 */
@Composable
private fun CategoryPanel(
    items: List<CashbackItem>,
    order: SortOrder<CashbackSummary.Order>,
    onPick: (CashbackSummary.Order) -> Unit,
    onFlip: () -> Unit,
    onEditBank: (BankBrand) -> Unit
) {
    val rows = remember(items, order) { CashbackSummary.sort(CashbackSummary.byCategory(items), order) }
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sorting by remember { mutableStateOf(false) }
    // Rows glide to new places only after a sort change, not when a group opens.
    var reordering by remember { mutableStateOf(false) }
    // The flag drops by its own timer: picking the same order changes nothing else to wake it.
    LaunchedEffect(reordering) {
        if (reordering) {
            kotlinx.coroutines.delay(CLOSE_MS + 100L)
            reordering = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SortControl(
                label = orderTitle(order.sort).lowercase(),
                reversed = order.reversed,
                directionLabel = orderDirection(order),
                onFlip = { reordering = true; onFlip() },
                onOpen = { sorting = true }
            )
        }
        Panel {
            // A new sort order moves the rows to their places (U-27).
            LookaheadScope {
            Column(Modifier.padding(vertical = 4.dp)) {
                val play = rememberCascade("cashback-categories")
                rows.forEachIndexed { i, row ->
                    key(row.groupKey + "|" + row.category) {
                    Column(Modifier.reorderable(this@LookaheadScope) { reordering }) {
                    if (i > 0) {
                        androidx.compose.material3.HorizontalDivider(
                            color = Slitok.Line,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                    val key = row.groupKey + "|" + row.category
                    // U-45: no long press — a bank's cashback is changed with its pencil.
                    Box(Modifier.cascade(i, play)) {
                        CategoryRowView(row, expanded = key in open) {
                            open = if (key in open) open - key else open + key
                        }
                    }
                    }
                    }
                }
            }
            }
        }
    }
    if (sorting) {
        SortSheet(
            options = CashbackSummary.Order.entries,
            order = order,
            title = ::orderTitle,
            direction = ::orderDirection,
            onPick = { reordering = true; onPick(it); sorting = false },
            onDismiss = { sorting = false }
        )
    }
}

private fun orderTitle(o: CashbackSummary.Order): String = when (o) {
    CashbackSummary.Order.PERCENT -> "По проценту"
    CashbackSummary.Order.ALPHABET -> "По названию"
    CashbackSummary.Order.BANK -> "По банку"
    CashbackSummary.Order.BANKS -> "По числу банков"
}

/** Where the list starts. */
private fun orderDirection(o: SortOrder<CashbackSummary.Order>): String {
    val r = o.reversed
    return when (o.sort) {
        CashbackSummary.Order.PERCENT -> if (r) "сначала низкий" else "сначала высокий"
        CashbackSummary.Order.ALPHABET, CashbackSummary.Order.BANK -> if (r) "Я → А" else "А → Я"
        CashbackSummary.Order.BANKS -> if (r) "сначала меньше" else "сначала больше"
    }
}

private const val MAX_STACK = 5

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun CategoryRowView(row: CashbackSummary.CategoryRow, expanded: Boolean, onToggle: () -> Unit) {
    val unfolds = row.offers.size > 1
    Column(Modifier.fillMaxWidth().clickable(enabled = unfolds, onClick = onToggle)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(row.iconKey, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                row.category,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            BankStack(row.bestBanks.take(MAX_STACK))
            // Other banks hide behind the best logos: "+N" says the row unfolds (F-19).
            val more = row.moreBanks + (row.bestBanks.size - MAX_STACK).coerceAtLeast(0)
            if (more > 0) MoreBadge(more)
            PercentText(row.best, best = true)
            Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterEnd) {
                if (unfolds) {
                    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = Slitok.Muted,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn }
                    )
                }
            }
        }
        AnimatedVisibility(expanded && unfolds) {
            Column(Modifier.padding(start = 64.dp, end = 14.dp, bottom = 10.dp)) {
                row.offers.forEach { o ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                        ru.maxrubtsoff.bankagg.ui.common.BankIcon(o.brand, size = 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            o.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slitok.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        PercentText(o.percent, best = o.percent == row.best, small = true)
                    }
                }
            }
        }
    }
}

/** "+2" tucked under the logo stack. */
@Composable
private fun MoreBadge(count: Int) {
    Box(
        Modifier
            .offset(x = (-6).dp)
            .zIndex(-1f)
            .height(22.dp)
            .widthIn(min = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Slitok.PanelTop)
            .border(1.dp, Slitok.Line, RoundedCornerShape(11.dp))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("+$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Slitok.Fg)
    }
}

/** Logos stacked with an overlap, first on top. */
@Composable
private fun BankStack(banks: List<BankBrand>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        banks.forEachIndexed { i, b ->
            Box(Modifier.zIndex((banks.size - i).toFloat())) {
                ru.maxrubtsoff.bankagg.ui.common.BankIcon(b, size = 26.dp)
            }
        }
    }
}

/** The percent column: Unbounded brass, the same width everywhere; lower offers in dimmed brass. */
@Composable
private fun PercentText(percent: Double, best: Boolean, small: Boolean = false) {
    Text(
        formatPercent(percent),
        style = (if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
            .copy(fontFamily = ru.maxrubtsoff.bankagg.ui.theme.Unbounded),
        fontWeight = FontWeight.SemiBold,
        color = if (best) Slitok.Brass2 else Slitok.Brass2.copy(alpha = 0.55f),
        textAlign = TextAlign.End,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = Modifier.widthIn(min = 54.dp)
    )
}

@Composable
private fun MonthPage(
    vm: CashbackViewModel,
    monthKey: String,
    items: List<CashbackItem>?,
    byCategory: Boolean,
    onEditBank: (BankBrand) -> Unit,
    onOpenBatch: (List<String>) -> Unit
) {
    val shots by remember(monthKey) { vm.shots(monthKey) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val byBrand = remember(items) { items.orEmpty().groupBy { it.brand }.toList().sortedBy { it.first.title } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberTabScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // F-38: screens waiting for review sit on top, one plate, opening the batch screen.
        if (shots.isNotEmpty()) {
            PendingShotsPlate(
                shots = shots.size,
                thumbs = shots.map { it.imagePath },
                ready = shots.count { it.status == ShotStatus.RECOGNIZED },
                check = shots.count { it.status == ShotStatus.NEEDS_INPUT },
                unread = shots.count { it.status == ShotStatus.FAILED },
                onOpen = { onOpenBatch(shots.map { it.id }) }
            )
        }
        if (items == null) {
            // Not loaded yet: nothing to show, nothing to offer.
        } else if (byBrand.isEmpty()) {
            LocalTourOffer.current(TourSection.CASHBACK, false)?.let { tour ->
                PlateButton(onClick = tour, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null)
                    Text("Как это работает")
                }
            }
        } else if (byCategory) {
            val order by vm.categoryOrder.collectAsStateWithLifecycle()
            CategoryPanel(items.orEmpty(), order, vm::pickCategoryOrder, vm::flipCategoryOrder, onEditBank)
        } else {
            val play = rememberCascade("cashback-banks")
            byBrand.forEachIndexed { i, (brand, list) ->
                Box(Modifier.cascade(i, play)) {
                BankCashbackPanel(brand, list, onEditBank = { onEditBank(brand) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * A bank's cashback for the month (U-47): its logo and name, the pencil (the
 * one way to change it, U-45), and its categories as plates that wrap onto new
 * lines — every name whole. The base rate is the dashed plate «На всё».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankCashbackPanel(
    brand: BankBrand,
    items: List<CashbackItem>,
    onEditBank: () -> Unit
) {
    Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.BANK_PANEL, active = brand == TourSample.BANK)) {
        Column(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ru.maxrubtsoff.bankagg.ui.common.BankIcon(brand, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    brand.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onEditBank) {
                    Icon(Icons.Filled.Edit, contentDescription = "Изменить кэшбэк банка", tint = Slitok.Brass2, modifier = Modifier.size(20.dp))
                }
            }
            val (base, regular) = items.partition(CashbackSpotlight::isBase)
            FlowRow(
                Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                regular.sortedByDescending { it.percent }.forEach { ValueChip(it.category, formatPercent(it.percent)) }
                base.sortedByDescending { it.percent }.forEach { ValueChip("На всё", formatPercent(it.percent), dashed = true) }
            }
        }
    }
}

