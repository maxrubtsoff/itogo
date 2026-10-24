package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackSpotlight
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.CategoryIcon
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The home cashback card. What to show is decided by [CashbackSpotlight]: the
 * four most useful categories, the month's maximum, and each bank's base rate.
 * Nothing to tick or configure. Tapping opens the Cashback tab.
 */
@Composable
fun CashbackCard(vm: CashbackViewModel, onOpen: () -> Unit, onOpenNextMonth: () -> Unit) {
    val s by vm.spotlight.collectAsStateWithLifecycle()
    val month = CashbackMonth.shortLabel(s.monthKey).lowercase()

    Panel(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Кэшбэк${RuText.DOT}$month",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("все", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = Slitok.Muted, modifier = Modifier.padding(start = 2.dp)
                )
            }

            if (s.missingThisMonth) {
                Text(
                    "Кэшбэк на $month не добавлен",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slitok.Muted
                )
                HintChip("Добавить скрин", onOpen)
            } else {
                // The base rate leads (U-8): it is what every other purchase earns.
                if (s.base.isNotEmpty()) BaseCashbackLine(s.base)
                s.rows.forEach { SpotRow(it, isMax = false) }
                s.maximum?.let { SpotRow(it, isMax = true) }
            }

            if (s.offerNextMonth) {
                val next = CashbackMonth.shortLabel(s.nextMonthKey).lowercase()
                HintChip("Выбрать кэшбэк на $next", onOpenNextMonth)
            }
        }
    }
}

/** "Базовый кэшбэк" and one chip per percent: bank logos + the rate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaseCashbackLine(groups: List<CashbackSpotlight.BaseGroup>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Базовый кэшбэк", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        groups.forEach { g ->
            val shape = RoundedCornerShape(99.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(Slitok.Inset)
                    .border(1.dp, Slitok.Line, shape)
                    .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BankStack(g.banks.take(3), g.banks.size - 3, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    formatPercent(g.percent),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slitok.Brass2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpotRow(row: CashbackSpotlight.Row, isMax: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(row.iconKey, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            row.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isMax) {
            Spacer(Modifier.width(6.dp))
            Text(
                "максимум",
                style = MaterialTheme.typography.labelMedium,
                color = Slitok.Brass2,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Slitok.BrassTint)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        BankStack(row.banks, row.extraBanks)
        Spacer(Modifier.width(10.dp))
        Text(
            formatPercent(row.percent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Slitok.Brass2,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/** Overlapping bank logos for a tie, plus "+N" when more share the best percent. */
@Composable
fun BankStack(banks: List<BankBrand>, extra: Int, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        banks.forEachIndexed { i, brand ->
            Box(Modifier.offset(x = if (i == 0) 0.dp else (-7 * i).dp)) {
                BankIcon(brand, size = size)
            }
        }
        if (extra > 0) {
            Spacer(Modifier.width(2.dp))
            Text("+$extra", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted, softWrap = false)
        }
    }
}

@Composable
private fun HintChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Slitok.BrassTint)
            .border(1.dp, Slitok.BrassLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Slitok.Brass2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Brass2)
    }
}

/** "5%", "1,5%", "0,25%" — Russian decimal comma. */
fun formatPercent(p: Double): String {
    val s = if (p % 1.0 == 0.0) p.toLong().toString()
    else java.math.BigDecimal.valueOf(p).stripTrailingZeros().toPlainString().replace('.', ',')
    return "$s%"
}
