package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** The one switch of the app (U-40/A1): brass when on, everywhere. */
@Composable
fun SlitokSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) Slitok.Brass else Slitok.Inset, label = "switchTrack")
    val thumb by animateColorAsState(if (checked) Slitok.OnBrassInk else Slitok.Muted, label = "switchThumb")
    val shift by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switchShift")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(shape)
            .background(track)
            .border(1.dp, if (checked) Slitok.Brass else Slitok.Line, shape)
            .then(
                if (onChange != null) Modifier.clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) } else Modifier
            )
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.offset(x = shift).size(20.dp).clip(CircleShape).background(if (enabled) thumb else thumb.copy(alpha = 0.45f)))
    }
}

/**
 * A row with a switch at its end: the whole row toggles. [icon] and [hint] are
 * optional; the hint is a quiet second line.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        icon?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        }
        SlitokSwitch(checked, onChange = null, enabled = enabled)
    }
}

/** A selectable plate (U-40/A7): brass when picked. [leading] is an optional icon or bank logo. */
@Composable
fun SlitokChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    /** A low plate for a row under a chart (F-45). */
    compact: Boolean = false
) {
    val shape = if (leading != null) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
    val bg by animateColorAsState(if (selected) Slitok.BrassPill else Slitok.Inset, label = "chipBg")
    val fg by animateColorAsState(if (selected) Slitok.Brass2 else Slitok.Muted, label = "chipFg")
    Row(
        modifier
            .pressScale()
            .height(if (leading != null) 44.dp else if (compact) 30.dp else 36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Slitok.BrassLine else Slitok.Line, shape)
            .clickable(onClick = onClick)
            .padding(start = if (leading != null) 4.dp else if (compact) 6.dp else 10.dp, end = if (leading != null) 16.dp else if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        leading?.invoke()
        Text(text, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

/** A plate with a label and a value, for a bank's cashback categories (U-47). [dashed] = base cashback. */
@Composable
fun ValueChip(label: String, value: String, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(34.dp)
            .clip(shape)
            .background(Slitok.Inset)
            .then(if (dashed) Modifier.dashedBorder(shape) else Modifier.border(1.dp, Slitok.Line, shape))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(value, style = Amounts.Small, color = Slitok.Brass2, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

private fun Modifier.dashedBorder(shape: RoundedCornerShape): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    onDrawBehind { drawOutline(outline, Slitok.Faint, style = stroke) }
}

/** What a tag says by its colour. */
enum class TagTone { Neutral, Mint, Brass, Violet, Coral }

/** A small status label (U-40/A7). Coral only for a real error. */
@Composable
fun SlitokTag(text: String, tone: TagTone = TagTone.Neutral, modifier: Modifier = Modifier) {
    val (fg, bg) = when (tone) {
        TagTone.Neutral -> Slitok.Muted to Slitok.Muted.copy(alpha = 0.12f)
        TagTone.Mint -> Slitok.Mint to Slitok.Mint.copy(alpha = 0.12f)
        TagTone.Brass -> Slitok.Brass2 to Slitok.BrassPill
        TagTone.Violet -> Slitok.VioletText to Slitok.Violet.copy(alpha = 0.14f)
        TagTone.Coral -> Slitok.Coral to Slitok.Coral.copy(alpha = 0.12f)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/** A state on one line, a dot before it, when an explanation follows below (F-31). */
@Composable
fun StatusLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A form field that picks one value from a list (F-31): a panel row with the
 * value (or [placeholder]) and an arrow. A tap opens the list in a sheet.
 * [leading] is the picked value's logo; none — a dashed circle.
 */
@Composable
fun SelectRow(
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Panel(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (value != null && leading != null) leading()
            else if (value == null) Box(Modifier.size(36.dp).dashedCircle())
            Text(
                value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) Slitok.Fg else Slitok.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint, modifier = Modifier.size(20.dp))
        }
    }
}

private fun Modifier.dashedCircle(): Modifier = this.drawWithCache {
    val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    onDrawBehind { drawCircle(Slitok.Faint, radius = size.minDimension / 2 - stroke.width, style = stroke) }
}

/** A caption over one figure, on its own panel (U-40/A8). With [onClick] the whole tile opens something (U-59). */
@Composable
fun StatTile(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    Panel(modifier) {
        Column(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            value()
        }
    }
}

/**
 * A section with nothing in it yet (F-31): its picture, one line of what lives
 * here, and the actions (add; «Как это работает» while the tour isn't done).
 */
@Composable
fun EmptyState(title: String, art: @Composable () -> Unit, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit) {
    Panel(modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            art()
            Text(title, style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}

/** A plain list row: what it is, then its value and an arrow when it opens something. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading?.invoke()
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint, modifier = Modifier.size(20.dp))
    }
}

/** A small round dot in a part's colour, before a row title. */
@Composable
fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}
