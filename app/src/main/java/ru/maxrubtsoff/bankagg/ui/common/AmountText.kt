package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask

/**
 * A one-line amount that never wraps and never clips its "₽": when it doesn't
 * fit, the font shrinks from [maxSize] down to [minSize]. The size is picked
 * in one pass with a [rememberTextMeasurer] before drawing, so the figure
 * doesn't visibly "jump" over several frames.
 *
 * Heroes use 34 → 20sp, tiles 18 → 12sp. [compact] is the short form of the
 * same sum ("1,77 млн ₽", [ru.maxrubtsoff.bankagg.domain.model.MoneyCompact.short]):
 * when the full text does not fit at [maxSize], it is shown instead, and only
 * then does the font shrink (F-40, B-72).
 */
@Composable
fun AmountText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxSize: TextUnit = style.fontSize,
    minSize: TextUnit = 12.sp,
    textAlign: TextAlign? = null,
    hideAll: Boolean = false,
    compact: String? = null
) = AmountText(AnnotatedString(text), modifier, style, color, fontWeight, maxSize, minSize, textAlign, hideAll, compact)

/**
 * When amounts are hidden ([LocalAmountsHidden]) the money in [text] turns into
 * dust; [hideAll] hides the whole figure (one without a currency sign). The
 * dust lies over the very figures at their fitted size, so hiding and showing
 * never move or resize anything (B-44). Kopecks follow the amount's step
 * ([maxSize]), not the shrunk size (U-56).
 */
@Composable
fun AmountText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxSize: TextUnit = style.fontSize,
    minSize: TextUnit = 12.sp,
    textAlign: TextAlign? = null,
    hideAll: Boolean = false,
    compact: String? = null
) {
    val measurer = rememberTextMeasurer()
    val base = style.merge(TextStyle(fontWeight = fontWeight, textAlign = textAlign ?: TextAlign.Unspecified))
    // The text wraps its content, so alignment is the box's job.
    val align = when (textAlign) {
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    val ink = amountInk(color, base)
    val look = kopeckLook(if (maxSize.isSp) maxSize else base.fontSize)
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    BoxWithConstraints(modifier, contentAlignment = align) {
        val maxW = constraints.maxWidth
        val top = if (maxSize.isSp) maxSize else base.fontSize
        fun widthOf(t: AnnotatedString, s: TextUnit) =
            measurer.measure(t, base.copy(fontSize = s), maxLines = 1, softWrap = false, constraints = Constraints()).size.width
        val chosen = remember(text, compact, maxW, base, top) {
            if (compact != null && maxW != Constraints.Infinity && widthOf(dimKopecks(text, ink, look), top) > maxW) AnnotatedString(compact) else text
        }
        val shown = remember(chosen, ink, look) { dimKopecks(chosen, ink, look) }
        val ranges = remember(chosen, hideAll) { MoneyMask.ranges(chosen.text, all = hideAll) }
        val size = remember(shown, maxW, base, maxSize, minSize) {
            fitSize(shown, maxW, base, maxSize, minSize) { s -> widthOf(shown, s) }
        }
        Text(
            shown,
            modifier = Modifier.amountDust(layout, ranges, ink),
            style = base.copy(fontSize = size),
            color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            softWrap = false,
            onTextLayout = { layout.value = it }
        )
    }
}

/**
 * The largest size in [minSize]..[maxSize] whose measured width fits [maxWidth].
 * Width grows almost linearly with the font size, so one proportional guess
 * lands close; a few small steps make it exact.
 */
private inline fun fitSize(
    text: AnnotatedString,
    maxWidth: Int,
    style: TextStyle,
    maxSize: TextUnit,
    minSize: TextUnit,
    widthAt: (TextUnit) -> Int
): TextUnit {
    val top = if (maxSize.isSp) maxSize.value else style.fontSize.value
    val floor = if (minSize.isSp) minSize.value else top
    if (maxWidth == Constraints.Infinity || text.isEmpty()) return top.sp
    val full = widthAt(top.sp)
    if (full <= maxWidth) return top.sp
    var s = (top * maxWidth / full.toFloat()).coerceIn(floor, top)
    var guard = 0
    while (s > floor && widthAt(s.sp) > maxWidth && guard++ < 12) {
        s = (s - 0.5f).coerceAtLeast(floor)
    }
    return s.sp
}
