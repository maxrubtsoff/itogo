package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The text of a bank's notification on an inset plate (F-51), whole and at once. [mark] is the piece read as the
 * sum, set in brass; the amounts hide behind dust like everywhere. With [onOpen] the whole plate leads to the
 * journal and ends in a chevron, with no caption.
 */
@Composable
fun BankNote(text: String, mark: IntRange?, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(14.dp)
    val body = remember(text, mark) {
        buildAnnotatedString {
            append(text)
            if (mark != null && mark.first >= 0 && mark.last < text.length) {
                addStyle(SpanStyle(color = Slitok.Brass2, background = Slitok.BrassPill, fontWeight = FontWeight.SemiBold), mark.first, mark.last + 1)
            }
        }
    }
    val ranges = remember(text) { MoneyMask.ranges(text) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    Row(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, Slitok.Line, shape)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            body, Modifier.weight(1f).amountDust(layout, ranges, Slitok.Fg),
            style = MaterialTheme.typography.bodyMedium, color = Slitok.Fg,
            onTextLayout = { layout.value = it }
        )
        if (onOpen != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Журнал", tint = Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}

/** «Было → станет» of one figure (F-51): the label on the left, the two sums at the right edge. */
@Composable
fun WasBecomeLine(label: String, was: String, became: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        MoneyText(was, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        Text("→", style = MaterialTheme.typography.bodyMedium, color = Slitok.Faint)
        MoneyText(became, style = MaterialTheme.typography.bodyMedium, color = Slitok.Fg, fontWeight = FontWeight.SemiBold)
    }
}

/** A line of a sheet that leads on to an object or a journal (F-51): the text and a chevron at the end. */
@Composable
fun LinkLine(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}
