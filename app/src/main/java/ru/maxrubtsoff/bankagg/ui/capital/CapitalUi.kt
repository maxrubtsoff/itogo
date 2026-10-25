package ru.maxrubtsoff.bankagg.ui.capital

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.RollingAmount
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.ui.common.DeltaLine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** Colour of a capital part: the dot, the bar segment, the hero glow. */
fun CapitalPart.color(): Color = when (this) {
    CapitalPart.ACCOUNTS -> Slitok.Brass
    CapitalPart.DEPOSITS -> Slitok.Violet
    CapitalPart.CURRENCY -> Slitok.Sky
    CapitalPart.CASH -> Slitok.Sand
}

val CapitalPart.label: String
    get() = when (this) {
        CapitalPart.ACCOUNTS -> "Счета"
        CapitalPart.DEPOSITS -> "Вклады"
        CapitalPart.CURRENCY -> "Валюта"
        CapitalPart.CASH -> "Наличные"
    }

/** "+4 700 ₽" / "−1 200 ₽" / "0 ₽" with a real minus sign. */
fun signedRub(minor: Long): String = when {
    minor > 0 -> "+" + Money.ofMinor(minor).format()
    minor < 0 -> "−" + Money.ofMinor(-minor).format()
    else -> Money.ZERO.format()
}

/** "+3,4%" / "−0,5%". */
fun signedPct(fraction: Double): String {
    val v = Math.round(fraction * 1000) / 10.0
    val abs = kotlin.math.abs(v)
    val s = if (abs % 1.0 == 0.0) abs.toLong().toString() else abs.toString().replace('.', ',')
    return when {
        v > 0 -> "+$s%"
        v < 0 -> "−$s%"
        else -> "0%"
    }
}

fun gainColor(minor: Long): Color = if (minor < 0) Slitok.Coral else Slitok.Mint


/** A small round dot in a part's colour; hollow when the part is out of the total. */
@Composable
fun PartDot(part: CapitalPart, included: Boolean, size: Dp = 10.dp) {
    val c = part.color()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (included) Modifier.background(c) else Modifier.border(1.5.dp, Slitok.Faint, CircleShape))
    )
}

/** Currency mark in a circle: "$", "€", or the code for currencies without a symbol. */
@Composable
fun CurrencyBadge(code: String, size: Dp = 38.dp) {
    val symbol = Currencies.of(code)?.symbol ?: code
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Slitok.Sky.copy(alpha = 0.28f), Slitok.Sky.copy(alpha = 0.12f))))
            .border(1.dp, Slitok.Sky.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = Slitok.Fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (symbol.length <= 1) 17.sp else if (symbol.length == 2) 13.sp else 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/** Section hero: caption, big amount, optional lines below. */
@Composable
fun SectionHeroText(caption: String, amount: String, color: Color = Slitok.Fg) {
    Column {
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // The sum rolls to a new value instead of jumping (U-19).
        RollingAmount(amount.filter { it.isDigit() }.toLongOrNull() ?: 0L) {
            AmountText(amount, style = MaterialTheme.typography.displaySmall, color = color, minSize = 20.sp)
        }
    }
}

/**
 * The month's change under a section's hero sum (F-36): a line with the little
 * graph; a tap opens the history with this part chosen. Absent until there are
 * two days of history.
 */
@Composable
fun HeroDelta(vm: CapitalViewModel, today: java.time.LocalDate, part: CapitalPart, onOpen: (CapitalPart) -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val glance = remember(days, today, part) { CapitalHistory.glance(days, today, part) } ?: return
    DeltaLine(Money.ofMinor(glance.change.deltaMinor), "за месяц", glance.spark, color = part.color(), onClick = { onOpen(part) })
}
