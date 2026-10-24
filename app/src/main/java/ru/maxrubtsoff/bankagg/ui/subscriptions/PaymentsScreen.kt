package ru.maxrubtsoff.bankagg.ui.subscriptions

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeaderGap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.ui.common.AddButton
import ru.maxrubtsoff.bankagg.ui.common.NotificationAccessBanner
import ru.maxrubtsoff.bankagg.ui.common.PagerTabs
import ru.maxrubtsoff.bankagg.ui.common.rememberNotificationAccess
import ru.maxrubtsoff.bankagg.ui.common.SectionPager
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The «Платежи» tab (F-27, U-47): one switch, «Кэшбэк · Подписки», turned by a
 * tap or a swipe. The cashback's month and its view (by bank / by category) sit
 * in the title row, so nothing else stacks above the list.
 */
@Composable
fun PaymentsScreen(
    cashback: @Composable (monthOffset: Int, byCategory: Boolean, onMonthOffset: (Int) -> Unit) -> Unit,
    subscriptions: @Composable () -> Unit,
    onAddSubscription: () -> Unit,
    startSection: Int = 0,
    initialOffset: Int = 0,
    showCashback: Boolean = true,
    showSubscriptions: Boolean = true,
    showNotifAccess: Boolean = false
) {
    val notifAccess by rememberNotificationAccess()
    // F-30: only the kept sections; with one of them there is no switch at all.
    val sections = listOfNotNull(0.takeIf { showCashback }, 1.takeIf { showSubscriptions }).ifEmpty { listOf(0) }
    val pager = rememberPagerState(initialPage = sections.indexOf(startSection).coerceAtLeast(0)) { sections.size }
    val section = sections[pager.currentPage.coerceIn(0, sections.lastIndex)]
    val scope = rememberCoroutineScope()
    var monthOffset by rememberSaveable { mutableIntStateOf(initialOffset) }
    var byCategory by rememberSaveable { mutableStateOf(false) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    val anchor = CashbackMonth.currentKey()
    val monthKey = CashbackMonth.keyForOffset(anchor, monthOffset)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Платежи") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (section == 0) {
                    MonthChip(CashbackMonth.shortLabel(monthKey)) { pickingMonth = true }
                    ViewToggle(byCategory) { byCategory = it }
                } else {
                    AddButton("Добавить подписку", onAddSubscription)
                }
            }
        }
        Box(Modifier.height(ScreenHeaderGap))
        // Without accounts the Overview may be gone; the access banner lives here then (U-52).
        if (showNotifAccess && !notifAccess) {
            NotificationAccessBanner(text = "Подписки не находятся — нет доступа к уведомлениям")
            Box(Modifier.height(12.dp))
        }
        if (sections.size > 1) {
            PagerTabs(sections.map { if (it == 0) "Кэшбэк" else "Подписки" }, pager) { scope.launch { pager.animateScrollToPage(it) } }
            Box(Modifier.height(12.dp))
        }
        SectionPager(pager, Modifier.fillMaxWidth().weight(1f)) { page ->
            if (sections[page] == 0) cashback(monthOffset, byCategory) { monthOffset = it } else subscriptions()
        }
    }

    if (pickingMonth) {
        MonthSheet(anchor, monthOffset, onPick = { monthOffset = it; pickingMonth = false }, onDismiss = { pickingMonth = false })
    }
}

/** «Октябрь ▾»: the month of the cashback in view. */
@Composable
private fun MonthChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .pressScale()
            .height(36.dp)
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, Slitok.Line, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Выбрать месяц", tint = Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}

/** By bank / by category, as two icons (U-47): a tap, no swipe — the swipe turns «Кэшбэк · Подписки». */
@Composable
private fun ViewToggle(byCategory: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.height(36.dp).clip(shape).background(Slitok.Inset).border(1.dp, Slitok.Line, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToggleIcon(Icons.Filled.AccountBalance, "По банкам", !byCategory) { onChange(false) }
        ToggleIcon(Icons.Filled.GridView, "По категориям", byCategory) { onChange(true) }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 36.dp, height = 30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Slitok.BrassPill else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (on) Slitok.Brass2 else Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}

/** The months to pick from: half a year back and the next one. */
@Composable
private fun MonthSheet(anchor: String, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    SlitokSheet(onDismiss) {
        Text("Месяц", style = MaterialTheme.typography.titleLarge)
        Column {
            for (offset in 1 downTo -6) {
                val key = CashbackMonth.keyForOffset(anchor, offset)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(offset) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        CashbackMonth.label(key),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (offset == current) Slitok.Brass2 else Slitok.Fg,
                        modifier = Modifier.weight(1f)
                    )
                    if (offset == current) Icon(Icons.Filled.Check, contentDescription = "Выбран", tint = Slitok.Brass2)
                }
            }
        }
    }
}
