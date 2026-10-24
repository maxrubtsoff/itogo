package ru.maxrubtsoff.bankagg.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The icons of installed bank apps and the user's own pictures, already decoded
 * (U-55/P2, P3). One of these is provided at the app root, so an icon reads only
 * its own entry and the others don't redraw when the cache fills.
 */
@Stable
class BankImages(
    val icons: State<Map<String, Bitmap>>,
    val pictures: State<Map<String, Bitmap?>>,
    val request: (String) -> Unit
)

val LocalBankImages = staticCompositionLocalOf<BankImages?> { null }

/** Subscribes to the icon cache once; call it high in the tree and provide the result as [LocalBankImages]. */
@Composable
fun rememberBankImages(): BankImages {
    val cache = (LocalContext.current.applicationContext as App).container.bankIcons
    val icons = cache.icons.collectAsStateWithLifecycle()
    val pictures = cache.pictures.collectAsStateWithLifecycle()
    return remember(cache) { BankImages(icons, pictures, cache::loadPicture) }
}

/** Where a picture is: not decoded yet, ready, or its file can't be read. */
@Immutable
internal sealed interface Picture {
    data object Loading : Picture
    data object Missing : Picture
    data class Ready(val bitmap: Bitmap) : Picture
}

/** The picture at [path], decoded in the background; recomposes only this reader when it is ready. */
@Composable
internal fun rememberPicture(path: String?, images: BankImages): State<Picture> {
    if (path != null) LaunchedEffect(path) { images.request(path) }
    return remember(path, images) {
        derivedStateOf {
            if (path == null) Picture.Missing
            else images.pictures.value.let { all -> if (path !in all) Picture.Loading else all[path]?.let(Picture::Ready) ?: Picture.Missing }
        }
    }
}

/** A rounded square for subscriptions; the corner grows with the avatar. */
fun squircle(size: Dp): Shape = RoundedCornerShape(size * 0.28f)

/** Letters on [BrandDecor]'s colour: black or white, whichever reads, unless the user set one. */
fun BrandDecor.letterColor(): Color =
    textColorArgb?.let { Color(it) } ?: if (Color(colorArgb).luminance() > 0.6f) Color.Black else Color.White

/**
 * A bank's or a subscription's face (F-33): the user's picture, or up to three
 * letters on a gradient coin. [overlay] — a small bank mark in the corner (where
 * a subscription is charged from). [picked] — a picture just chosen, not saved
 * yet (the editor's preview).
 */
@Composable
fun BrandAvatar(
    decor: BrandDecor,
    shape: Shape,
    size: Dp,
    overlay: BankBrand? = null,
    modifier: Modifier = Modifier,
    picked: Any? = null,
    contentDescription: String? = null
) {
    val images = LocalBankImages.current ?: rememberBankImages()
    val picture by rememberPicture(decor.logoFile.takeIf { picked == null }, images)
    Box(modifier.size(size)) {
        val face = Modifier
            .size(size)
            .shadow(5.dp, shape, clip = false, spotColor = Color.Black)
            .clip(shape)
        val ready = picture as? Picture.Ready
        when {
            picked != null -> Image(
                painter = rememberAsyncImagePainter(picked),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = face.background(Color(decor.colorArgb)).border(1.dp, Slitok.Line, shape)
            )
            ready != null -> PictureFace(ready.bitmap, contentDescription, shape, face)
            // The file is still being read: the plain colour, no letters flashing in between.
            decor.logoFile != null && picture is Picture.Loading -> Box(face.background(Color(decor.colorArgb)).border(1.dp, Slitok.IconRing, shape))
            else -> Coin(decor, shape, size, face)
        }
        if (overlay != null) {
            val mark = (size.value * 0.45f).coerceAtLeast(14f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(mark * 0.18f, mark * 0.18f)
                    .clip(CircleShape)
                    .background(Slitok.PanelBottom)
                    .padding(1.5.dp)
            ) { BankIcon(overlay, size = mark) }
        }
    }
}

/** A real picture (an installed app's icon or the user's own) on a hairline ring, dimmed a touch to sit in the palette. */
@Composable
internal fun PictureFace(bitmap: Bitmap, contentDescription: String?, shape: Shape, modifier: Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.tint(Slitok.IconDim, BlendMode.Darken),
        modifier = modifier.border(1.dp, Slitok.Line, shape)
    )
}

@Composable
private fun Coin(decor: BrandDecor, shape: Shape, size: Dp, modifier: Modifier) {
    val base = Color(decor.colorArgb)
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(base.lighten(), base),
                    center = Offset(size.value * 0.9f, size.value * 0.6f),
                    radius = size.value * 2.2f
                )
            )
            .border(1.dp, Slitok.IconRing, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = decor.letters,
            color = decor.letterColor(),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * when (decor.letters.length) { 1 -> 0.42f; 2 -> 0.34f; else -> 0.27f }).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Color.lighten(): Color = Color(
    red = (red + (1f - red) * 0.35f).coerceIn(0f, 1f),
    green = (green + (1f - green) * 0.35f).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * 0.35f).coerceIn(0f, 1f),
    alpha = alpha
)
