package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The strip under a sum in a hero (F-36): how much the capital changed over a
 * period, the little line of it and a chevron; a tap opens the history. A rise
 * is mint, a fall keeps the text colour (not coral — a fall is not an alarm),
 * nothing — «без изменений за месяц» in muted. Sums go through [MoneyText], so
 * hidden amounts hide here too. [spark] null — no little line.
 */
@Composable
fun DeltaLine(
    delta: Money,
    period: String,
    spark: List<Long>?,
    modifier: Modifier = Modifier,
    color: Color = Slitok.Brass2,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (delta.minor == 0L) {
            MoneyText("без изменений $period", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, modifier = Modifier.weight(1f, fill = false))
        } else {
            val up = delta.minor > 0L
            MoneyText(
                (if (up) "+" else "−") + Money.ofMinor(kotlin.math.abs(delta.minor)).format(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (up) Slitok.Mint else Slitok.Fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )
            MoneyText(period, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (spark != null && delta.minor != 0L) Sparkline(spark, color)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "История капитала", tint = Slitok.Faint, modifier = Modifier.size(20.dp))
    }
}
