package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File

/**
 * «4 скрина ждут проверки» (F-38, U-64): one plate under the section's action row,
 * the same in deposits and cashback. A stack of up to three thumbnails, the count,
 * and a line of group statuses; a tap opens the batch screen. A status with 0 is not shown.
 */
@Composable
fun PendingShotsPlate(
    shots: Int,
    thumbs: List<String>,
    ready: Int = 0,
    check: Int = 0,
    unread: Int = 0,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Slitok.BrassTint, Slitok.PanelTop)))
            .border(1.dp, Slitok.BrassLine, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThumbStack(thumbs.take(3))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(RuPlural.pendingShots(shots), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ready > 0) Status(RuPlural.readyShots(ready), Slitok.Mint)
                if (check > 0) Status("$check проверить", Slitok.Brass2)
                if (unread > 0) Status(RuPlural.unreadShots(unread), Slitok.Muted)
            }
        }
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Slitok.Brass2, modifier = Modifier.rotate(-90f))
    }
}

@Composable
private fun Status(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

/** Thumbnails laid one over another, each a little to the right. */
@Composable
private fun ThumbStack(paths: List<String>) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.width((28 + 10 * (paths.size.coerceAtLeast(1) - 1)).dp).height(44.dp)) {
        paths.forEachIndexed { i, path ->
            Box(
                Modifier
                    .offset(x = (10 * i).dp)
                    .size(width = 28.dp, height = 44.dp)
                    .clip(shape)
                    .background(Slitok.Inset)
                    .border(1.dp, Slitok.Line, shape)
            ) {
                if (path.isNotBlank() && File(path).exists()) {
                    Image(
                        rememberAsyncImagePainter(ShotImage(path)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    )
                }
            }
        }
    }
}
