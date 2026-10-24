package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Quick actions on a long press (U-7, U-23): a light vibration, the screen goes
 * into shadow and only the pressed element stays above it, raised, with a
 * «Слиток» menu next to it — under it, or over it when there is no room below.
 * A dangerous action ([danger]) is red and sits apart at the bottom.
 */
@Composable
fun LongPressMenu(
    items: List<Pair<String, () -> Unit>>,
    danger: Set<String> = emptySet(),
    content: @Composable (onLongPress: () -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    Box(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) {
        content {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            open = true
        }
    }
    if (open) {
        MenuOverlay(bounds, items, danger, onClose = { open = false }) { content {} }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun MenuOverlay(
    bounds: Rect,
    items: List<Pair<String, () -> Unit>>,
    danger: Set<String>,
    onClose: () -> Unit,
    element: @Composable () -> Unit
) {
    val animate = Motion.enabled
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { shown.animateTo(1f, Motion.tween(Motion.FAST)) }
    fun close(then: () -> Unit = {}) {
        scope.launch {
            if (animate) shown.animateTo(0f, Motion.tween(Motion.FAST))
            onClose(); then()
        }
    }
    val density = LocalDensity.current
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val regular = items.filter { it.first !in danger }
    val risky = items.filter { it.first in danger }
    val menuH = with(density) { (12 + ITEM_H * items.size + if (risky.isNotEmpty() && regular.isNotEmpty()) 9 else 0).dp.toPx() }
    // B-40: the popup is measured to the visible frame; the veil must reach under the gesture bar too.
    val root = LocalView.current.rootView
    val fullW = with(density) { root.width.toDp() }
    val fullH = with(density) { root.height.toDp() }
    val gap = with(density) { 10.dp.toPx() }
    val below = bounds.bottom + gap + menuH < screenH - with(density) { 24.dp.toPx() }
    val menuTop = if (below) bounds.bottom + gap else (bounds.top - gap - menuH).coerceAtLeast(gap)

    Popup(
        popupPositionProvider = FullWindow,
        onDismissRequest = { close() },
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        BlurBehind(animate)
        Box(Modifier.wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true).requiredSize(fullW, fullH)) {
            // The veil: a tap on it closes the menu.
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = shown.value }
                    .background(Slitok.ScrimDeep.copy(alpha = 0.72f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
            )
            // The pressed element, in its place, raised above the veil.
            Box(
                Modifier
                    .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        val s = 1f + 0.03f * shown.value
                        scaleX = s; scaleY = s
                        shadowElevation = 18.dp.toPx() * shown.value
                        shape = RoundedCornerShape(18.dp)
                        clip = true
                    }
                    // A row inside a list panel has no background of its own: give the raised copy one.
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.PanelBottom)))
            ) {
                // The raised copy is a second instance of the row in another window: without
                // the shared-transition scope its sharedCard key isn't registered twice (B-28).
                CompositionLocalProvider(LocalSharedScope provides null, LocalNavScope provides null) { element() }
            }
            Column(
                Modifier
                    .offset { IntOffset((bounds.left + with(density) { 4.dp.toPx() }).toInt(), menuTop.toInt()) }
                    .width(IntrinsicSize.Max).widthIn(min = 240.dp, max = fullW - 40.dp)
                    .graphicsLayer {
                        alpha = shown.value
                        translationY = (1f - shown.value) * 8.dp.toPx() * (if (below) -1 else 1)
                        shadowElevation = 20.dp.toPx()
                        shape = RoundedCornerShape(20.dp)
                        clip = true
                    }
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.PanelBottom)))
                    .border(1.dp, Slitok.Line, RoundedCornerShape(20.dp))
                    .padding(6.dp)
            ) {
                // U-44: compact rows, quiet icons; coral only for the dangerous one.
                regular.forEach { (label, action) -> MenuItem(label, iconFor(label), Slitok.Fg, Slitok.Muted) { close(action) } }
                if (risky.isNotEmpty() && regular.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Slitok.Line)
                }
                risky.forEach { (label, action) -> MenuItem(label, iconFor(label), Slitok.Coral, Slitok.Coral) { close(action) } }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, color: Color, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ITEM_H.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val ITEM_H = 44

/** On Android 12+ the screen under the veil is blurred too; below that the veil is enough. */
@Composable
private fun BlurBehind(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !enabled) return
    val view = LocalView.current
    DisposableEffect(view) {
        runCatching {
            val root = view.rootView
            val lp = root.layoutParams as WindowManager.LayoutParams
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 24
            (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, lp)
        }
        onDispose {}
    }
}

/** Places the popup over the whole window, so the veil covers everything. */
private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/** One icon per kind of action, by its label. */
private fun iconFor(label: String): ImageVector = when {
    label.startsWith("Купил") -> Icons.Filled.Add
    label.startsWith("Продал") -> Icons.Filled.Remove
    label.startsWith("Операции") -> Icons.AutoMirrored.Filled.List
    label.startsWith("Пополнить") -> Icons.Filled.ArrowUpward
    label.startsWith("Удалить") -> Icons.Filled.Delete
    label.startsWith("Убрать") -> Icons.Filled.Close
    label.startsWith("Не учитывать") -> Icons.Filled.VisibilityOff
    label.startsWith("Учитывать") -> Icons.Filled.Visibility
    else -> Icons.Filled.Edit
}
