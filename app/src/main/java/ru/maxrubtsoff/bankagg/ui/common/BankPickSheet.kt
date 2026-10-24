package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * «Банк» (U-39): the one way to pick a single bank for a form — the person's
 * banks in a sheet, the picked one checked; a search when the list is long.
 * Opened from a [SelectRow]. With [onClear] the first row is «Не указан» — the
 * bank can be taken off again (checked while nothing is picked).
 */
@Composable
fun BankPickSheet(selected: BankBrand?, onPick: (BankBrand) -> Unit, onDismiss: () -> Unit, onClear: (() -> Unit)? = null) {
    val all = pickable(LocalMyBanks.current, selected)
    var query by rememberSaveable { mutableStateOf("") }
    val shown = if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), ignoreCase = true) }
    SlitokSheet(onDismiss) {
        Text("Банк", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
        if (all.size > SEARCH_FROM) SlitokField(query, { query = it }, placeholder = "Найти банк")
        Column {
            if (onClear != null && query.isBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onClear(); onDismiss() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Не указан", style = MaterialTheme.typography.bodyLarge, color = Slitok.Muted, modifier = Modifier.weight(1f))
                    if (selected == null) Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = Slitok.Brass2, modifier = Modifier.size(22.dp))
                }
            }
            shown.forEach { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(b); onDismiss() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BankIcon(b, size = 36.dp)
                    Text(b.title, style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (b.key == selected?.key) Icon(Icons.Filled.Check, contentDescription = "Выбран", tint = Slitok.Brass2, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

private const val SEARCH_FROM = 6
