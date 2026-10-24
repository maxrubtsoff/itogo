package ru.maxrubtsoff.bankagg.ui.subscriptions

import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeaderGap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.ui.common.NotificationAccessBanner
import ru.maxrubtsoff.bankagg.ui.common.PagerTabs
import ru.maxrubtsoff.bankagg.ui.common.rememberNotificationAccess
import ru.maxrubtsoff.bankagg.ui.common.SectionPager

/**
 * The «Платежи» tab (F-27, U-47): one switch, «Кэшбэк · Подписки», turned by a
 * tap or a swipe. The header holds the title only (U-58): the cashback's month
 * and view and every «+» sit in each section's own row of actions.
 */
@Composable
fun PaymentsScreen(
    cashback: @Composable (monthOffset: Int, byCategory: Boolean, onMonthOffset: (Int) -> Unit, onByCategory: (Boolean) -> Unit) -> Unit,
    subscriptions: @Composable () -> Unit,
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
    val scope = rememberCoroutineScope()
    var monthOffset by rememberSaveable { mutableIntStateOf(initialOffset) }
    var byCategory by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Платежи")
        Box(Modifier.height(ScreenHeaderGap))
        if (sections.size > 1) {
            PagerTabs(sections.map { if (it == 0) "Кэшбэк" else "Подписки" }, pager) { scope.launch { pager.animateScrollToPage(it) } }
            Box(Modifier.height(12.dp))
        }
        SectionPager(pager, Modifier.fillMaxWidth().weight(1f)) { page ->
            if (sections[page] == 0) {
                cashback(monthOffset, byCategory, { monthOffset = it }, { byCategory = it })
            } else {
                // Without accounts the Overview may be gone; the access banner lives on
                // the page that needs it — subscriptions (U-52) — and not on the cashback.
                Column(Modifier.fillMaxSize()) {
                    if (showNotifAccess && !notifAccess) {
                        NotificationAccessBanner(text = "Подписки не находятся — нет доступа к уведомлениям")
                        Box(Modifier.height(12.dp))
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) { subscriptions() }
                }
            }
        }
    }
}
