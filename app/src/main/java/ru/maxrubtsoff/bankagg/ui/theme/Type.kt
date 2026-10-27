package ru.maxrubtsoff.bankagg.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * Type scale: Unbounded for display/titles, Golos Text for everything else.
 * Figures (U-73): a standalone value is Unbounded via [Amounts]; a figure inside a phrase
 * or a date stays in the phrase's Golos style.
 * Body and label styles break lines by paragraph (balanced, no lone last word)
 * and never hyphenate.
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    bodyMedium = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    bodySmall = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    labelLarge = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    labelMedium = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    // U-40/A18: every style is set, so nothing falls back to the system font.
    titleSmall = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    labelSmall = TextStyle(
        fontFamily = GolosText, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp,
        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None
    ),
    headlineMedium = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 30.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 34.sp
    ),
    displayMedium = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp
    ),
    displayLarge = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp
    )
)

/**
 * The amount scale (F-31, U-40/A9, U-73): every standalone figure — sum, percent,
 * rate, term, badge, axis label — takes one of these. Unbounded with figures of
 * one width, so columns line up. A figure inside a phrase ("на 100 ₽", a date)
 * stays in the phrase's text style (Golos).
 */
object Amounts {
    private const val TABULAR = "tnum"

    /** The one main figure of a screen. */
    val Hero = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp
    )

    /** Summaries and totals. */
    val Large = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp
    )

    /** A sum in a list row. */
    val Row = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp
    )

    /** Income and secondary sums in a row. */
    val Small = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR,
        fontSize = 13.sp, lineHeight = 16.sp
    )

    /** Badges, chart labels. */
    val Caption = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR,
        fontSize = 11.sp, lineHeight = 14.sp
    )
}
