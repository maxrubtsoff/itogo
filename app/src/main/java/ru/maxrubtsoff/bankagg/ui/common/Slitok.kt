package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** Vertical brass gradient used for money accents and the segmented thumb. */
val BrassBrush = Brush.verticalGradient(listOf(Slitok.Brass2, Slitok.Brass))

/** A layered, beveled panel: gradient fill, hairline border, soft drop shadow. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .shadow(16.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.PanelBottom)))
            .border(1.dp, Slitok.Line, shape),
        content = content
    )
}

/** Soft radial glow painted behind a hero figure for depth (brass by default). */
fun Modifier.heroGlow(color: Color = Slitok.Brass): Modifier = this.drawWithCache {
    // Built once per size, not on every frame of a screen transition (B-18).
    val center = Offset(size.width * 0.3f, size.height * 0.25f)
    val radius = size.width * 0.75f
    val brush = Brush.radialGradient(
        colors = listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0f)),
        center = center,
        radius = radius
    )
    onDrawBehind { drawCircle(brush = brush, center = center, radius = radius) }
}
