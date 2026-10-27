package ru.maxrubtsoff.bankagg.ui.common

import ru.maxrubtsoff.bankagg.ui.theme.Amounts
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** The collecting banks as overlapping icons, the rest as «+N». [size] and [overlap] make a smaller stack (the settings tile). */
@Composable
fun BankStack(banks: List<BankBrand>, max: Int = 5, size: Dp = 38.dp, overlap: Dp = 10.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        banks.take(max).forEachIndexed { i, b ->
            Box(
                Modifier.offset(x = -overlap * i).clip(CircleShape).background(Slitok.PanelTop).padding(2.dp)
            ) { BankIcon(b, size = size) }
        }
        if (banks.size > max) {
            Box(
                Modifier.offset(x = -overlap * max).size(size + 4.dp).clip(CircleShape).background(Slitok.Inset),
                contentAlignment = Alignment.Center
            ) { Text("+${banks.size - max}", style = Amounts.Caption, color = Slitok.Muted) }
        }
    }
}
