package ru.maxrubtsoff.bankagg.ui.deposits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ---- formatting ----

private val MONTHS_SHORT = listOf(
    "янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"
)
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun monthShort(ym: YearMonth): String = MONTHS_SHORT[ym.monthValue - 1]
fun formatDate(d: LocalDate): String = d.format(DATE_FMT)

/** "21.10". */
fun shortDate(d: LocalDate): String = "%02d.%02d".format(d.dayOfMonth, d.monthValue)

/** "1 800 000 ₽". */
fun money(m: Money): String = m.format()

/** A deposit's name (B-33): its own, or «Вклад 11,45%» when it has none. */
fun depositName(d: ru.maxrubtsoff.bankagg.domain.model.Deposit): String =
    d.title?.takeIf { it.isNotBlank() } ?: "Вклад ${ratePercent(d.rateBp)}"

/** Just the digits group, no currency: "1 800 000". */
fun amountDigits(m: Money): String = m.formatNumber()

/** "+36 598 ₽" / "0 ₽". */
fun signedMoney(m: Money): String = if (m.minor > 0) "+${m.format()}" else m.format()

/** "19%", "19,5%", "19,25%" — as many decimals as the rate has. */
fun ratePercent(rateBp: Int): String {
    val whole = rateBp / 100
    val frac = rateBp % 100
    val s = when {
        frac == 0 -> "$whole"
        frac % 10 == 0 -> "$whole,${frac / 10}"
        else -> "$whole,%02d".format(frac)
    }
    return "$s%"
}

fun methodLabel(method: AccrualMethod): String = when (method) {
    AccrualMethod.LINEAR -> "Линейно"
    AccrualMethod.CAPITALIZATION -> "Капитализация"
    AccrualMethod.PAYOUT -> "Выплата %"
}

/** "осталось 42 дня" / "завершён". */
fun remainingLabel(endDate: LocalDate, asOf: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(asOf, endDate)
    return when {
        days < 0 -> "завершён"
        days == 0L -> "сегодня конец"
        else -> "осталось ${RuPlural.days(days.toInt())}"
    }
}

/** "40 дн." / "сегодня" / "завершён" — for the compact list. */
fun daysLeftShort(endDate: LocalDate, asOf: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(asOf, endDate)
    return when {
        days < 0 -> "завершён"
        days == 0L -> "сегодня"
        else -> "$days дн."
    }
}

// ---- rate chip ----
