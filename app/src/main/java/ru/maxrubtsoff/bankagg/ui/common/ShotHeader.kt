package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File

/**
 * The head of every screen that checks a recognised screenshot (U-53, F-34,
 * B-51): cashback, deposit and their batches look the same. While the app
 * reads, the screenshot stands big with the scan line over it («Читаем скрин»,
 * [ScanningThumb]); when reading ends it settles into a compact panel — the
 * thumbnail, a dot and a word for the state, one quiet line under it. A tap on
 * the thumbnail opens the screenshot whole. The two are one element: the panel
 * grows and shrinks in place, the screen below never swaps (B-51).
 *
 * [status] null — not loaded yet. [hint] is the quiet line; without it the
 * state's own default is used. Not recognised is not an error of the person, so
 * it is grey, not red (B-35).
 */
@Composable
fun ShotHeader(imagePath: String, status: ShotStatus?, modifier: Modifier = Modifier, hint: String? = null) {
    var photoOpen by remember { mutableStateOf(false) }
    val mode = when (status) {
        null -> HeaderMode.LOADING
        ShotStatus.PROCESSING -> HeaderMode.READING
        else -> HeaderMode.DONE
    }
    AnimatedContent(
        targetState = mode,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(Motion.tween(Motion.NORMAL)) togetherWith fadeOut(Motion.tween(Motion.FAST)))
                .using(SizeTransform(clip = false) { _, _ -> Motion.tween<IntSize>(Motion.NORMAL, Motion.Standard) })
        },
        contentAlignment = Alignment.TopCenter,
        label = "shotHeader"
    ) { m ->
        when (m) {
            HeaderMode.LOADING -> Box(Modifier.fillMaxWidth().height(COMPACT_HEIGHT))
            HeaderMode.READING -> Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
                ScanningThumb(
                    status = ShotStatus.PROCESSING,
                    modifier = Modifier.size(width = BIG_WIDTH, height = BIG_HEIGHT).tourTarget(TourTarget.SHOT_THUMB)
                ) { ShotPicture(imagePath, ContentScale.Crop) }
            }
            HeaderMode.DONE -> {
                val (label, color, text) = describe(status)
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .size(width = 52.dp, height = 92.dp)
                                .clip(shape)
                                .border(1.dp, Slitok.Line, shape)
                                .background(Slitok.Inset)
                                .tourTarget(TourTarget.SHOT_THUMB)
                                .clickable { photoOpen = true },
                            contentAlignment = Alignment.Center
                        ) { ShotPicture(imagePath, ContentScale.Crop) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusLine(label, color)
                            Text(hint ?: text, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                        }
                    }
                }
            }
        }
    }
    if (photoOpen) SlitokSheet(onDismiss = { photoOpen = false }) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(shape).border(1.dp, Slitok.Line, shape).background(Slitok.Inset),
            contentAlignment = Alignment.Center
        ) { ShotPicture(imagePath, ContentScale.Fit) }
    }
}

private enum class HeaderMode { LOADING, READING, DONE }

private val COMPACT_HEIGHT = 116.dp
private val BIG_WIDTH = 168.dp
private val BIG_HEIGHT = 300.dp

/** The dot's word, its colour and the quiet line by default. */
private fun describe(status: ShotStatus?): Triple<String, androidx.compose.ui.graphics.Color, String> = when (status) {
    ShotStatus.RECOGNIZED -> Triple("Распознано", Slitok.Mint, "Сверьте со скрином и сохраните")
    ShotStatus.NEEDS_INPUT -> Triple("Проверьте", Slitok.Brass2, "Допишите, чего не хватает")
    ShotStatus.FAILED -> Triple("Скрин не прочитался", Slitok.Muted, "Заполните по скрину")
    else -> Triple("Читаем скрин", Slitok.Brass2, "Это несколько секунд")
}

/**
 * The picture of a screenshot, or — where there is no file (a tour reads an
 * example, not a real screenshot) — a drawing of one: lines of text on a dark card.
 */
@Composable
internal fun BoxScope.ShotPicture(path: String, scale: ContentScale) {
    val exists = remember(path) { path.isNotBlank() && File(path).exists() }
    if (exists) {
        Image(
            painter = rememberAsyncImagePainter(ShotImage(path)),
            contentDescription = "Скрин целиком",
            contentScale = scale,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )
    } else if (path.isBlank()) {
        Column(
            Modifier.fillMaxSize().background(Slitok.PanelBottom).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0.6f, 1f, 0.45f, 1f, 0.7f, 1f, 0.55f, 0.8f, 0.4f, 0.65f, 1f, 0.5f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Slitok.Line))
            }
        }
    } else {
        Text("Фото недоступно", style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, modifier = Modifier.align(Alignment.Center).padding(4.dp))
    }
}
