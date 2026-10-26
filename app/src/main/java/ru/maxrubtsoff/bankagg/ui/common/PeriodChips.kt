package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import androidx.compose.ui.unit.dp

/** «Неделя · Месяц · Год · Всё»: the plates of a history period (F-36). */
val HistoryPeriod.label: String
    get() = when (this) {
        HistoryPeriod.WEEK -> "Неделя"
        HistoryPeriod.MONTH -> "Месяц"
        HistoryPeriod.YEAR -> "Год"
        HistoryPeriod.ALL -> "Всё"
    }

/** «за неделю» … «за всё время»: how a change is named next to its sum. */
val HistoryPeriod.spanWord: String
    get() = when (this) {
        HistoryPeriod.WEEK -> "за неделю"
        HistoryPeriod.MONTH -> "за месяц"
        HistoryPeriod.YEAR -> "за год"
        HistoryPeriod.ALL -> "за всё время"
    }

@Composable
fun PeriodChips(selected: HistoryPeriod, onPick: (HistoryPeriod) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        HistoryPeriod.entries.forEach { p ->
            SlitokChip(p.label, selected = p == selected, onClick = { onPick(p) }, modifier = Modifier.weight(1f), compact = compact)
        }
    }
}
