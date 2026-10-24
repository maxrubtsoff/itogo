package ru.maxrubtsoff.bankagg.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Слиток" palette — a committed dark, precious-metal identity.
 * Brass is the money accent and accounts, mint is positive, coral negative,
 * violet marks deposits, sky — currency, sand — cash.
 */
object Slitok {
    val Bg = Color(0xFF0A0F0E)
    val Bg2 = Color(0xFF0E1413)
    val PanelTop = Color(0xFF18211F)
    val PanelBottom = Color(0xFF111917)
    val Inset = Color(0xFF0C1211)

    val Fg = Color(0xFFEEF3F0)
    val Muted = Color(0xFF8EA39B)
    val Faint = Color(0xFF56655F)
    val Line = Color(0x14FFFFFF)        // white @ ~8%

    val Brass = Color(0xFFD7A74A)
    val Brass2 = Color(0xFFF0C878)
    val OnBrass = Color(0xFF1A130A)
    val Mint = Color(0xFF59E2A6)
    val Coral = Color(0xFFFF8468)
    val Violet = Color(0xFF9A86F5)
    val VioletLight = Color(0xFFBBA6FF)   // gradient end on violet bars
    val VioletText = Color(0xFFC9B8FF)    // text on a violet tint

    /** Capital parts: currency and cash. */
    val Sky = Color(0xFF6CC4E8)
    val Sand = Color(0xFFCBB89A)

    // Tints and overlays used across screens.
    val BrassTint = Color(0x1FD7A74A)     // brass @ 12% — soft accent fill
    val BrassPill = Color(0x26D7A74A)     // brass @ 15% — active pill
    val BrassLine = Color(0x55D7A74A)     // brass @ 33% — accent border
    val BrassEdge = Color(0x8CD7A74A)     // brass @ 55% — strong accent border
    val OnBrassInk = Color(0xFF14120E)    // text on a bright brass fill
    val Scrim = Color(0x8C050807)         // dark veil over photos
    val ScrimLight = Color(0x57050807)
    val ScrimDeep = Color(0xF5050807)
    // The tour's light card (F-30): stands out of the dark screen it explains.
    val CoachBg = Color(0xFFF3EEE3)
    val CoachInk = Color(0xFF14120E)
    val CoachText = Color(0xFF3B372E)
    val CoachQuiet = Color(0xFF6B6556)
    val IconDim = Color(0x1A000000)       // dims real app icons to sit in the palette
    val IconRing = Color(0x22FFFFFF)      // hairline ring around bank icons
}
