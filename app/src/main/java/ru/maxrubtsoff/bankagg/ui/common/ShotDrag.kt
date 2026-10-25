package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File
import kotlin.math.roundToInt

/**
 * Moving a screenshot to another group with a finger (U-65): a long press lifts
 * it, it follows the finger, and where it lets go decides. Over a group — it goes
 * there (the caller does the same move as «Перенести в…»); over [ShotDropZone] —
 * to a group of its own; anywhere else — back where it was, nothing happens.
 *
 * Wiring: one [rememberShotDragState] per screen; [shotDragSource] on each thumbnail,
 * [shotDropTarget] on each group, [ShotDropZone] after the groups and one
 * [ShotDragLayer] over the whole screen for the flying thumbnail.
 */
@Stable
class ShotDragState internal constructor(private val drop: () -> (String, Any?) -> Unit) {
    /** The screenshot being carried, null when nothing is. */
    var dragging by mutableStateOf<String?>(null)
        private set

    /** The target under the finger (a group key), null when none. */
    var over by mutableStateOf<Any?>(null)
        private set

    internal var path by mutableStateOf("")
        private set
    internal var pointer by mutableStateOf(Offset.Zero)
        private set

    /** The target that just received a shot and the count of its flash, so the same one can flash again. */
    internal var flash by mutableStateOf<Pair<Any, Int>?>(null)
        private set

    private var flashes = 0
    internal val bounds = mutableMapOf<Any, Rect>()

    internal fun start(shotId: String, imagePath: String, at: Offset) {
        path = imagePath
        pointer = at
        dragging = shotId
        over = hit()
    }

    internal fun move(by: Offset) {
        pointer += by
        over = hit()
    }

    internal fun end() {
        val id = dragging ?: return
        val target = hit()
        dragging = null
        over = null
        if (target != null) {
            flash = target to ++flashes
            drop()(id, if (target === NewGroup) null else target)
        }
    }

    internal fun cancel() {
        dragging = null
        over = null
    }

    private fun hit(): Any? = bounds.entries.firstOrNull { pointer in it.value }?.key

    internal object NewGroup
}

/** [onDrop] gets the shot and the key of the group it was dropped on; null key — «Отдельный…». */
@Composable
fun rememberShotDragState(onDrop: (shotId: String, target: Any?) -> Unit): ShotDragState {
    val latest by rememberUpdatedState(onDrop)
    return remember { ShotDragState { latest } }
}

/** A thumbnail that can be lifted with a long press and carried. */
fun Modifier.shotDragSource(state: ShotDragState, shotId: String, imagePath: String): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current
    this
        .onGloballyPositioned { coords = it }
        .graphicsLayer { alpha = if (state.dragging == shotId) 0.35f else 1f }
        .pointerInput(shotId) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(shotId, imagePath, coords?.localToRoot(offset) ?: offset)
                },
                onDrag = { change, delta -> change.consume(); state.move(delta) },
                onDragEnd = { state.end() },
                onDragCancel = { state.cancel() }
            )
        }
}

/** A group a thumbnail can be dropped on: lit while the finger is over it, flashes brass when it receives one. */
fun Modifier.shotDropTarget(state: ShotDragState, key: Any): Modifier = composed {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.flash) {
        val f = state.flash
        if (f != null && f.first == key) {
            flash.snapTo(1f)
            flash.animateTo(0f, Motion.tween(Motion.NORMAL))
        }
    }
    DisposableEffect(key) { onDispose { state.bounds.remove(key) } }
    val over = state.over == key
    this
        .onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }
        .drawWithContent {
            drawContent()
            val r = CornerRadius(18.dp.toPx())
            if (over) {
                drawRoundRect(Slitok.BrassTint, cornerRadius = r)
                drawRoundRect(Slitok.BrassEdge, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
            }
            if (flash.value > 0f) drawRoundRect(Slitok.Brass2.copy(alpha = 0.35f * flash.value), cornerRadius = r)
        }
}

/** The thumbnail in the air: lifted (×1.08, brass glow) and under the finger. Put it over the whole screen. */
@Composable
fun ShotDragLayer(state: ShotDragState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val lifted by animateFloatAsState(
        if (state.dragging != null && Motion.enabled) 1.08f else 1f,
        animationSpec = Motion.tween(Motion.FAST),
        label = "lift"
    )
    val w = 40.dp
    val h = 64.dp
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().zIndex(10f).onGloballyPositioned { origin = it.positionInRoot() }) {
        if (state.dragging != null) {
            val at = state.pointer - origin
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .offset { IntOffset((at.x - with(density) { w.toPx() } / 2).roundToInt(), (at.y - with(density) { h.toPx() } / 2).roundToInt()) }
                    .size(w, h)
                    .graphicsLayer { scaleX = lifted; scaleY = lifted }
                    .let { if (Motion.enabled) it.heroGlow(Slitok.Brass) else it }
                    .clip(shape)
                    .background(Slitok.Inset)
                    .border(1.dp, Slitok.BrassEdge, shape)
            ) {
                if (state.path.isNotBlank() && File(state.path).exists()) {
                    Image(
                        rememberAsyncImagePainter(ShotImage(state.path)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** «Отдельный вклад»: opens while a thumbnail is carried; dropping on it makes a group of its own. */
@Composable
fun ShotDropZone(state: ShotDragState, label: String) {
    AnimatedVisibility(
        visible = state.dragging != null,
        enter = fadeIn(Motion.tween(Motion.NORMAL)) + expandVertically(Motion.tween(Motion.NORMAL, Motion.Standard)),
        exit = fadeOut(Motion.tween(Motion.FAST)) + shrinkVertically(Motion.tween(Motion.FAST, Motion.Standard))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shotDropTarget(state, ShotDragState.NewGroup)
                .drawBehind {
                    drawRoundRect(
                        Slitok.BrassLine,
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())))
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Slitok.Brass2)
        }
    }
}
