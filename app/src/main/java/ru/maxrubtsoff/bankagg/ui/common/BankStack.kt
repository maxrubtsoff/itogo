package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** The collecting banks as overlapping icons, the rest as «+N». */
@Composable
fun BankStack(banks: List<BankBrand>, max: Int = 5) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        banks.take(max).forEachIndexed { i, b ->
            Box(
                Modifier.offset(x = (-10 * i).dp).clip(CircleShape).background(Slitok.PanelTop).padding(2.dp)
            ) { BankIcon(b, size = 38.dp) }
        }
        if (banks.size > max) {
            Box(
                Modifier.offset(x = (-10 * max).dp).size(42.dp).clip(CircleShape).background(Slitok.Inset),
                contentAlignment = Alignment.Center
            ) { Text("+${banks.size - max}", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted) }
        }
    }
}
