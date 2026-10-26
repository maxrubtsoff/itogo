package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File

/**
 * «Распознаю 2 из 6…» while OCR runs over the batch (F-34): a screenshot with
 * the scan line over it, the count under it and a bar. The groups come in where
 * this stands, not on another screen (B-51).
 */
@Composable
fun BatchProgress(done: Int, total: Int) {
    Panel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScanningThumb(status = ShotStatus.PROCESSING, modifier = Modifier.size(width = 84.dp, height = 150.dp), readingText = "Распознаю $done из $total…") {
                ShotPicture("", ContentScale.Crop)
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = Slitok.Brass,
                trackColor = Slitok.Line
            )
        }
    }
}

/** A group's screenshots as small thumbnails; tapping one opens [onOpen], a long press carries it when [drag] is given (U-65). */
@Composable
fun ShotThumbs(
    paths: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    drag: ShotDragState? = null,
    trailing: (@Composable () -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEach { (id, path) ->
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .size(width = 40.dp, height = 64.dp)
                    .clip(shape)
                    .background(Slitok.Inset)
                    .border(1.dp, Slitok.Line, shape)
                    .clickable { onOpen(id) }
                    .let { if (drag != null) it.shotDragSource(drag, id, path) else it }
            ) {
                if (path.isNotBlank() && File(path).exists()) {
                    Image(
                        rememberAsyncImagePainter(ShotImage(path)),
                        contentDescription = "Скрин",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    )
                }
                if (onRemove != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(Slitok.Inset.copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 8.dp))
                            .clickable(onClickLabel = "Убрать скрин") { onRemove(id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать скрин", tint = Slitok.Coral, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        trailing?.invoke()
    }
}

/** The dashed «+» that ends a deposit's row of screenshots (F-38): adds one more. */
@Composable
fun AddShotTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(width = 40.dp, height = 64.dp)
            .clip(shape)
            .background(Slitok.Inset)
            .drawBehind {
                drawRoundRect(
                    Slitok.BrassLine,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                )
            }
            .clickable(onClickLabel = "Добавить скрин", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Добавить скрин", tint = Slitok.Brass2)
    }
}

/** A screenshot that waits for review, as a row of [AddShotSheet]. */
data class PendingShotRow(val id: String, val path: String, val title: String, val subtitle: String?)

/** «Добавить скрин»: the shots waiting for review come first, «Из галереи» is the quiet row after them. */
@Composable
fun AddShotSheet(pending: List<PendingShotRow>, onPick: (String) -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    SlitokSheet(onDismiss) {
        Text("Добавить скрин", style = MaterialTheme.typography.titleMedium)
        if (pending.isNotEmpty()) {
            SheetList {
                pending.forEach { row ->
                    SheetRow(
                        row.title,
                        subtitle = row.subtitle,
                        leading = { SheetThumb(row.path) },
                        onClick = { onPick(row.id) }
                    )
                }
            }
        }
        SheetList {
            SheetRow("Из галереи", leading = { SheetIconTile(Icons.Filled.AddPhotoAlternate) }, arrow = true, onClick = onGallery)
        }
    }
}

@Composable
private fun SheetThumb(path: String) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.size(width = 32.dp, height = 52.dp).clip(shape).background(Slitok.Inset).border(1.dp, Slitok.Line, shape)) {
        if (path.isNotBlank() && File(path).exists()) {
            Image(
                rememberAsyncImagePainter(ShotImage(path)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The screenshot large, and where it can go: another group ([targets], by label),
 * a group of its own ([newLabel]), or out of the batch.
 */
@Composable
fun MoveShotSheet(
    path: String,
    targets: List<String>,
    newLabel: String,
    onMove: (Int?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    SlitokSheet(onDismiss) {
        if (path.isNotBlank() && File(path).exists()) {
            Image(
                rememberAsyncImagePainter(ShotImage(path)),
                contentDescription = "Скрин",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(12.dp))
            )
        }
        Text("Перенести в…", style = MaterialTheme.typography.titleMedium)
        Column {
            targets.forEachIndexed { i, label -> SheetOption(Icons.AutoMirrored.Filled.ArrowForward, label) { onMove(i) } }
            SheetOption(Icons.Filled.Add, newLabel) { onMove(null) }
            SheetOption(Icons.Filled.DeleteOutline, "Убрать скрин", danger = true, onClick = onRemove)
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Slitok.Coral else Slitok.Muted)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) Slitok.Coral else Slitok.Fg)
    }
}
