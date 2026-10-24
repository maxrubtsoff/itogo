package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.text.style.TextOverflow
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.luminance
import ru.maxrubtsoff.bankagg.App
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Bank token. Uses the installed app's real icon (clipped to a circle, with a
 * hairline ring and a touch of dimming so it sits in the dark palette), or a
 * branded gradient coin with the bank's initial when the app isn't installed.
 */
@Composable
fun BankIcon(brand: BankBrand, size: Dp = 40.dp) {
    val context = LocalContext.current
    val cache = remember { (context.applicationContext as App).container.bankIcons }
    val icons by cache.icons.collectAsState()
    // The app's icon first, then the user's own picture (F-22), then letters.
    val own = remember(brand.logoFile) {
        brand.logoFile?.let { f -> runCatching { BitmapFactory.decodeFile(f) }.getOrNull() }
    }
    val bitmap = icons[brand] ?: own
    val shape = CircleShape

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = brand.title,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Slitok.IconDim, BlendMode.Darken),
            modifier = Modifier
                .size(size)
                .shadow(5.dp, shape, clip = false, spotColor = Color.Black)
                .clip(shape)
                .border(1.dp, Slitok.Line, shape)
        )
    } else {
        val base = Color(brand.primaryColor)
        Box(
            modifier = Modifier
                .size(size)
                .shadow(5.dp, shape, clip = false, spotColor = Color.Black)
                .clip(shape)
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
                text = brand.letters,
                color = brand.textColor?.let { Color(it) } ?: if (brand.onPrimaryDark || Color(brand.primaryColor).luminance() > 0.6f) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * when (brand.letters.length) { 1 -> 0.42f; 2 -> 0.34f; else -> 0.27f }).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Color.lighten(): Color = Color(
    red = (red + (1f - red) * 0.35f).coerceIn(0f, 1f),
    green = (green + (1f - green) * 0.35f).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * 0.35f).coerceIn(0f, 1f),
    alpha = alpha
)
