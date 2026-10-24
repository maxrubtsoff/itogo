package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/*
 * The «Слитки» button set (U-13), the only buttons in the app:
 * - BrassButton — the one main action of a screen (save, add);
 * - PlateButton — a secondary action, a dark plate in the Panel style;
 * - TileButton — a big plate with an icon, two side by side (add sheets, backup);
 * - ChipButton — a small action inside a row;
 * - AddButton — the brass "+" next to a list's sort control.
 */

private val Shape = RoundedCornerShape(16.dp)

/** Brass bar with a soft top highlight; [content] is the label (and an icon). */
@Composable
fun BrassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .pressScale()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Shape)
            .background(Brush.verticalGradient(listOf(Slitok.Brass2, Slitok.Brass)))
            .drawWithContent {
                drawContent()
                drawLine(Color.White.copy(alpha = 0.35f), Offset(16.dp.toPx(), 1f), Offset(size.width - 16.dp.toPx(), 1f), 1.dp.toPx())
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides Slitok.OnBrassInk) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

/** Dark plate, brass label: a secondary action. */
@Composable
fun PlateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .pressScale()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Shape)
            .background(Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.Inset)))
            .border(1.dp, Slitok.Line, Shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides Slitok.Brass2) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

/** Coral plate: an action that takes something away (remove a bank, delete). */
@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .pressScale()
            .heightIn(min = 52.dp)
            .clip(Shape)
            .background(if (filled) Slitok.Coral else Slitok.Coral.copy(alpha = 0.08f))
            .border(1.dp, Slitok.Coral.copy(alpha = if (filled) 0f else 0.4f), Shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides if (filled) Slitok.OnBrassInk else Slitok.Coral) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

/** A big plate: an icon in a brass square, the title under it. */
@Composable
fun TileButton(icon: ImageVector, title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .pressScale()
            .height(104.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.Inset)))
            .border(1.dp, Slitok.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Slitok.BrassTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Slitok.Brass2, modifier = Modifier.size(22.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Slitok.Fg, maxLines = 2)
    }
}

/** Small action in a row: thin frame, brass text. */
@Composable
fun ChipButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .pressScale()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, Slitok.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Slitok.Brass2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The brass "+" beside a list's sort control. */
@Composable
fun AddButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .pressScale()
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Slitok.Brass2, Slitok.Brass)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = contentDescription, tint = Slitok.OnBrassInk)
    }
}

/**
 * A quiet text action (U-40/A5): brass for an action, muted for declining
 * («Пропустить», «Отмена»). Instead of the stock TextButton.
 */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, quiet: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (quiet) Slitok.Muted else Slitok.Brass2,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/**
 * Two buttons side by side (B-38): always halves of one height, labels on one
 * line. When a label doesn't fit its half, the buttons stand one under the other.
 */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val half = (width - gap * (measurables.size - 1)) / measurables.size.coerceAtLeast(1)
        val fits = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }
        if (fits) {
            val height = measurables.maxOf { it.minIntrinsicHeight(half) }
            val placeables = measurables.map { it.measure(Constraints.fixed(half, height)) }
            layout(width, height) {
                placeables.forEachIndexed { i, p -> p.place(i * (half + gap), 0) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { p -> p.place(0, y); y += p.height + gap }
            }
        }
    }
}

/** "+" opens this: from screenshots or by hand — the same for cashback and deposits. */
@Composable
fun AddSheet(title: String, onShots: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    SlitokSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TileButton(Icons.Filled.AddPhotoAlternate, "Со скринов", Modifier.weight(1f)) { onDismiss(); onShots() }
            TileButton(Icons.Filled.Edit, "Вручную", Modifier.weight(1f)) { onDismiss(); onManual() }
        }
    }
}
