package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.compose.ui.geometry.Rect

/**
 * Where a tour lights a part and where its card stands (B-48): plain geometry in
 * px, so it can be tested. The light is always whole on the screen — a part that
 * reaches the edge or goes under a system bar is lit only as far as it can be seen.
 */
internal object TourFrame {

    /** The window for a framed part: [target] grown by [pad] and kept inside [safe]. */
    fun framed(target: Rect, pad: Float, safe: Rect): Rect {
        val l = maxOf(target.left - pad, safe.left)
        val t = maxOf(target.top - pad, safe.top)
        val r = minOf(target.right + pad, safe.right)
        val b = minOf(target.bottom + pad, safe.bottom)
        return Rect(l, t, maxOf(l, r), maxOf(t, b))
    }

    /**
     * The window for a part that is only dimmed round (a sheet, the bar): the
     * part itself, with the sides it touches in [screen] pushed [reach] beyond it,
     * so the corners that belong to the screen's edge are not rounded in the light.
     */
    fun dimmed(target: Rect, screen: Rect, reach: Float): Rect {
        val edge = 1f
        return Rect(
            if (target.left <= screen.left + edge) screen.left - reach else target.left,
            if (target.top <= screen.top + edge) screen.top - reach else target.top,
            if (target.right >= screen.right - edge) screen.right + reach else target.right,
            if (target.bottom >= screen.bottom - edge) screen.bottom + reach else target.bottom
        )
    }

    /** The corner of the window: the part's own, grown by what the window was grown by, never more than half a side. */
    fun radius(targetRadius: Float, grown: Float, window: Rect): Float =
        (targetRadius + grown).coerceAtMost(minOf(window.width, window.height) / 2f).coerceAtLeast(0f)

    /** The top of the card: under the part when it fits, else over it, else against the bottom (never over a window in the lower half); centred without a part. */
    fun cardTop(window: Rect?, cardHeight: Float, gap: Float, safe: Rect): Float {
        if (window == null) return (safe.top + (safe.height - cardHeight) / 2f).coerceAtLeast(safe.top)
        val below = window.bottom + gap
        if (below + cardHeight <= safe.bottom) return below
        val above = window.top - gap - cardHeight
        if (above >= safe.top) return above
        // No room either way: a window low on the screen (a sheet) is never laid over — up to the top edge instead.
        if (window.top > safe.top + safe.height / 2f) return above.coerceAtLeast(safe.top)
        return (safe.bottom - cardHeight).coerceAtLeast(safe.top)
    }
}
