package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.BankSlice
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.plural
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

private val MONTHS_IN = listOf(
    "январе","феврале","марте","апреле","мае","июне",
    "июле","августе","сентябре","октябре","ноябре","декабре"
)

/**
 * What a bank holds (F-53): sum, bar of composition, aggregate rows for only
 * what the bank actually has — accounts, deposits, cashback, subscriptions. For
 * the «rest» slice: currency and cash rows.
 */
@Composable
fun BankSheet(
    slice: BankSlice,
    monthIndex: Int,
    onDismiss: () -> Unit,
    onOpenPart: (CapitalPart) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenCashback: () -> Unit
) {
    SlitokSheet(onDismiss = onDismiss) {
        Header(slice)
        Spacer(Modifier.height(10.dp))
        if (slice.isRest) {
            RestRows(slice, onOpenPart = { onDismiss(); onOpenPart(it) })
        } else {
            BankRows(
                slice,
                monthIndex = monthIndex,
                onOpenPart = { onDismiss(); onOpenPart(it) },
                onOpenSubscriptions = { onDismiss(); onOpenSubscriptions() },
                onOpenCashback = { onDismiss(); onOpenCashback() }
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Header(slice: BankSlice) {
    val title = slice.brand?.title ?: "Не в банке"
    Row(verticalAlignment = Alignment.CenterVertically) {
        slice.brand?.let {
            BankIcon(it, size = 44.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(6.dp))
    AmountText(Money.ofMinor(slice.valueMinor).format(), style = Amounts.Hero, minSize = 20.sp, textAlign = TextAlign.Start)
    Spacer(Modifier.height(10.dp))
    CompositionBar(slice)
}

@Composable
private fun CompositionBar(slice: BankSlice) {
    val parts = if (slice.isRest) listOf(
        slice.restCurrencyMinor to Slitok.Sky,
        slice.restCashMinor to Slitok.Sand
    ) else listOf(
        slice.accountsTotalMinor to Slitok.Brass,
        slice.depositsTotalMinor to Slitok.Violet
    )
    val total = parts.sumOf { it.first.coerceAtLeast(0L) }.coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Slitok.Line)
    ) {
        parts.forEach { (v, color) ->
            if (v > 0L) Box(Modifier.weight(v.toFloat() / total).fillMaxWidth().height(4.dp).background(color))
        }
    }
}

@Composable
private fun BankRows(
    slice: BankSlice,
    monthIndex: Int,
    onOpenPart: (CapitalPart) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenCashback: () -> Unit
) {
    val show = mutableListOf<@Composable () -> Unit>()
    if (slice.accountsTotalMinor > 0L) {
        show += {
            DotRow(
                dot = Slitok.Brass,
                title = "Счета",
                subtitle = plural(slice.accountsCount, "счёт", "счёта", "счётов"),
                sumMinor = slice.accountsTotalMinor,
                onClick = { onOpenPart(CapitalPart.ACCOUNTS) }
            )
        }
    }
    if (slice.depositsTotalMinor > 0L) {
        show += {
            DotRow(
                dot = Slitok.Violet,
                title = "Вклады",
                subtitle = plural(slice.depositsCount, "вклад", "вклада", "вкладов"),
                sumMinor = slice.depositsTotalMinor,
                onClick = { onOpenPart(CapitalPart.DEPOSITS) }
            )
        }
    }
    SheetList { interleave(show) }
    val extras = mutableListOf<@Composable () -> Unit>()
    if (slice.hasCashback) {
        extras += {
            SheetRow(
                "Кэшбэк в ${MONTHS_IN[monthIndex.coerceIn(0, 11)]}",
                subtitle = plural(slice.monthCashbackCategories, "категория", "категории", "категорий"),
                arrow = true,
                onClick = onOpenCashback
            )
        }
    }
    if (slice.subscriptionsCount > 0) {
        extras += {
            SheetRow(
                "Подписки",
                subtitle = plural(slice.subscriptionsCount, "подписка", "подписки", "подписок"),
                arrow = true,
                onClick = onOpenSubscriptions,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AmountText(
                            Money.ofMinor(slice.subscriptionsMonthlyMinor).format(),
                            style = Amounts.Row, minSize = 12.sp, color = Slitok.Fg
                        )
                        Text(" в месяц", style = MaterialTheme.typography.bodySmall, color = Slitok.Faint)
                    }
                }
            )
        }
    }
    if (extras.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SheetList { interleave(extras) }
    }
}

@Composable
private fun RestRows(slice: BankSlice, onOpenPart: (CapitalPart) -> Unit) {
    val rows = mutableListOf<@Composable () -> Unit>()
    if (slice.restCurrencyMinor > 0L) {
        rows += {
            DotRow(
                dot = Slitok.Sky,
                title = "Валюта",
                subtitle = null,
                sumMinor = slice.restCurrencyMinor,
                onClick = { onOpenPart(CapitalPart.CURRENCY) }
            )
        }
    }
    if (slice.restCashMinor > 0L) {
        rows += {
            DotRow(
                dot = Slitok.Sand,
                title = "Наличные",
                subtitle = null,
                sumMinor = slice.restCashMinor,
                onClick = { onOpenPart(CapitalPart.CASH) }
            )
        }
    }
    if (rows.isNotEmpty()) SheetList { interleave(rows) }
}

@Composable
private fun interleave(items: List<@Composable () -> Unit>) {
    items.forEachIndexed { i, item ->
        if (i > 0) HorizontalDivider(color = Slitok.Line)
        item()
    }
}

@Composable
private fun DotRow(
    dot: Color,
    title: String,
    subtitle: String?,
    sumMinor: Long,
    onClick: () -> Unit
) {
    SheetRow(
        title,
        subtitle = subtitle,
        arrow = true,
        onClick = onClick,
        leading = {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        },
        trailing = {
            AmountText(
                Money.ofMinor(sumMinor).format(),
                style = Amounts.Row, minSize = 12.sp, color = Slitok.Fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}
