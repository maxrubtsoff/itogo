package ru.maxrubtsoff.bankagg.ui.deposits

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val RU = Locale("ru")
private val DAY_MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)

/** "Октябрь 2026". */
fun monthFull(ym: YearMonth): String =
    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() } + " " + ym.year

/** "31 октября". */
fun dayMonth(d: LocalDate): String = d.format(DAY_MONTH_FMT)

private val BAR_WIDTH = 40.dp
private val BAR_AREA = 140.dp

/**
 * The deposit by month: one bar per month of the term, all the same width, in a
 * ribbon that scrolls sideways (the current month starts at the right edge).
 * Violet body — the money, mint cap — that month's interest. A tap picks a
 * month; its figures sit above the ribbon.
 */
@Composable
fun MonthsChart(deposit: Deposit, asOf: LocalDate) {
    val bars = remember(deposit, asOf) { DepositMath.monthBars(deposit, asOf) }
    if (bars.isEmpty()) return
    val sizes = remember(bars) { DepositMath.barSizes(bars, BAR_AREA.value) }
    // Bars grow up from zero when the chart first shows (U-19).
    val grow = remember { Animatable(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(420)) }
    val nowIdx = bars.indexOfFirst { it.state == DepositMath.MonthState.CURRENT }.let { i ->
        when {
            i >= 0 -> i
            bars.first().state == DepositMath.MonthState.FUTURE -> 0
            else -> bars.lastIndex
        }
    }
    var picked by rememberSaveable(deposit.id) { mutableIntStateOf(nowIdx) }
    val sel = bars[picked.coerceIn(0, bars.lastIndex)]

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickedMonth(sel, payout = deposit.method == AccrualMethod.PAYOUT)

        // Reversed layout: item 0 sits at the right edge, so the current month
        // starts there with the months ahead just off-screen.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (bars.lastIndex - nowIdx).coerceAtLeast(0))
        LazyRow(
            state = listState,
            reverseLayout = true,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(bars.size) { k ->
                val i = bars.lastIndex - k
                val b = bars[i]
                val prevYear = bars.getOrNull(i - 1)?.month?.year
                MonthBarItem(
                    bar = b,
                    size = sizes[i].let { s -> DepositMath.BarSize(s.total * grow.value, s.cap * grow.value) },
                    // "янв 27" where the year turns.
                    label = monthShort(b.month) + if (prevYear != null && prevYear != b.month.year) " " + (b.month.year % 100) else "",
                    selected = i == picked,
                    onClick = { picked = i }
                )
            }
        }
    }
}

@Composable
private fun PickedMonth(bar: DepositMath.MonthBar, payout: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val past = bar.state == DepositMath.MonthState.PAST
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, Slitok.Line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthFull(bar.month),
                style = MaterialTheme.typography.labelLarge,
                color = Slitok.VioletText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                when (bar.state) {
                    DepositMath.MonthState.PAST -> "прошёл"
                    DepositMath.MonthState.CURRENT -> "сейчас"
                    DepositMath.MonthState.FUTURE -> "прогноз"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Slitok.Muted
            )
        }
        ChartFact("На вкладе", money(bar.balance), Slitok.Fg)
        ChartFact(
            when {
                payout && past -> "Выплачено за месяц"
                payout -> "Выплатим за месяц"
                past -> "Начислено за месяц"
                else -> "Начислим за месяц"
            },
            signedMoney(bar.income),
            Slitok.Mint
        )
    }
}

@Composable
private fun ChartFact(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, modifier = Modifier.weight(1f))
        MoneyText(
            value,
            style = Amounts.Small,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun MonthBarItem(
    bar: DepositMath.MonthBar,
    size: DepositMath.BarSize,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val total = size.total.dp.coerceAtLeast(10.dp)
    val cap = size.cap.dp.coerceAtMost(total)
    val shape = RoundedCornerShape(8.dp)
    val future = bar.state == DepositMath.MonthState.FUTURE
    val body = if (future) Brush.verticalGradient(listOf(Slitok.Violet.copy(alpha = 0.2f), Slitok.Violet.copy(alpha = 0.12f)))
        else Brush.verticalGradient(listOf(Slitok.VioletLight, Slitok.Violet))
    val capColor = when (bar.state) {
        DepositMath.MonthState.PAST -> Slitok.Mint
        DepositMath.MonthState.CURRENT -> Slitok.Mint.copy(alpha = 0.6f)
        DepositMath.MonthState.FUTURE -> Slitok.Mint.copy(alpha = 0.22f)
    }
    // No frame around the picked month (B-16): it stays bright, the others dim.
    val edge = if (future) Modifier.border(1.dp, Slitok.Violet.copy(alpha = 0.3f), shape) else Modifier
    val dim = if (selected) Modifier else Modifier.drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha = 0.28f)) }
    Column(
        Modifier
            .width(BAR_WIDTH)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(Modifier.fillMaxWidth().height(total).clip(shape).then(edge).then(dim)) {
            Box(Modifier.fillMaxWidth().height(cap).background(capColor))
            Box(Modifier.fillMaxWidth().weight(1f).background(body))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                selected -> Slitok.Brass2
                bar.state == DepositMath.MonthState.CURRENT -> Slitok.Fg
                else -> Slitok.Muted
            },
            fontWeight = if (selected || bar.state == DepositMath.MonthState.CURRENT) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}
