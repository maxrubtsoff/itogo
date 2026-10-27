package ru.maxrubtsoff.bankagg.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.util.concurrent.ConcurrentHashMap

/**
 * A bank's accent colour from its logo (F-53): the vibrant swatch, else the
 * dominant one. A grey logo gives nothing to hold on to — then the brand's own
 * colour, and brass when that is grey too. Found once per bank and kept.
 */
private object BankAccentCache {
    private const val MIN_SATURATION = 0.2f
    private val found = ConcurrentHashMap<String, Color>()

    fun cached(brand: BankBrand): Color? = found[brand.key]

    fun fallback(brand: BankBrand): Color {
        val own = Color(brand.primaryColor)
        return if (saturationOf(own.toArgb()) < MIN_SATURATION) Slitok.Brass else own
    }

    fun resolve(brand: BankBrand, logo: Bitmap?): Color {
        val color = logo?.let { bmp ->
            // Palette cannot read a hardware bitmap.
            val p = runCatching { Palette.from(bmp).maximumColorCount(12).generate() }.getOrNull()
            p?.let { (it.vibrantSwatch ?: it.dominantSwatch)?.takeIf { s -> s.hsl[1] >= MIN_SATURATION }?.let { s -> Color(s.rgb) } }
        } ?: fallback(brand)
        // No logo yet: do not keep the fallback, the palette colour must still get in later.
        if (logo != null) found[brand.key] = color
        return color
    }

    private fun saturationOf(argb: Int): Float {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
        return hsl[1]
    }
}

/** The accent colour of [brand]'s logo: the fallback at once, the logo's own as soon as it is read. */
@Composable
fun rememberBankAccent(brand: BankBrand): Color {
    val images = LocalBankImages.current ?: rememberBankImages()
    val own by rememberPicture(brand.logoFile, images)
    val logo = (own as? Picture.Ready)?.bitmap ?: images.icons.value[brand.key]
    val state by produceState(BankAccentCache.cached(brand) ?: BankAccentCache.fallback(brand), brand.key, logo) {
        value = withContext(Dispatchers.Default) { BankAccentCache.resolve(brand, logo) }
    }
    return state
}
