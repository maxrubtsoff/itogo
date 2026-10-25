package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand

/**
 * Bank token. The user's own picture comes first — it is their explicit choice
 * (B-49) — then the installed app's real icon (clipped to a circle, with a
 * hairline ring and a touch of dimming so it sits in the dark palette), then a
 * branded gradient coin with the bank's letters ([BrandAvatar]).
 *
 * Pictures are decoded off the main thread by the icon cache (U-55/P2); an icon
 * reads only its own entry of the shared state, so one finishing doesn't redraw
 * the rest (U-55/P3).
 */
@Composable
fun BankIcon(brand: BankBrand, size: Dp = 40.dp) {
    val images = LocalBankImages.current ?: rememberBankImages()
    val own by rememberPicture(brand.logoFile, images)
    val installed by remember(brand.key, images) { derivedStateOf { images.icons.value[brand.key] } }

    val ownReady = own as? Picture.Ready
    val app = installed
    when {
        ownReady != null -> PictureFace(ownReady.bitmap, brand.title, CircleShape, ring(size))
        // The own picture is still being read: hold the plain colour, don't flash the app's icon.
        brand.logoFile != null && own is Picture.Loading -> BrandAvatar(brand.decor(), CircleShape, size)
        app != null -> PictureFace(app, brand.title, CircleShape, ring(size))
        else -> BrandAvatar(brand.decor().copy(logoFile = null), CircleShape, size)
    }
}

private fun ring(size: Dp): Modifier =
    Modifier.size(size).shadow(5.dp, CircleShape, clip = false, spotColor = Color.Black).clip(CircleShape)
