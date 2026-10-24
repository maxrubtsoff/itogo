@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package ru.maxrubtsoff.bankagg.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import ru.maxrubtsoff.bankagg.R

private fun wght(w: Int) = FontVariation.Settings(FontVariation.weight(w))

/** Unbounded — display face for headings, the wordmark and money figures. */
val Unbounded = FontFamily(
    Font(R.font.unbounded_wght, FontWeight.Medium, variationSettings = wght(500)),
    Font(R.font.unbounded_wght, FontWeight.SemiBold, variationSettings = wght(600)),
    Font(R.font.unbounded_wght, FontWeight.Bold, variationSettings = wght(700))
)

/** Golos Text — interface and body face (native Cyrillic). */
val GolosText = FontFamily(
    Font(R.font.golos_text_wght, FontWeight.Normal, variationSettings = wght(400)),
    Font(R.font.golos_text_wght, FontWeight.Medium, variationSettings = wght(500)),
    Font(R.font.golos_text_wght, FontWeight.SemiBold, variationSettings = wght(600)),
    Font(R.font.golos_text_wght, FontWeight.Bold, variationSettings = wght(700))
)
