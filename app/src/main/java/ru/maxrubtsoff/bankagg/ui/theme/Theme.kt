package ru.maxrubtsoff.bankagg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * "Слиток" is a committed dark theme — no dynamic color, no light variant —
 * so the identity is consistent on every device.
 */
private val SlitokScheme = darkColorScheme(
    primary = Slitok.Brass,
    onPrimary = Slitok.OnBrass,
    primaryContainer = Slitok.PanelTop,
    onPrimaryContainer = Slitok.Brass2,
    secondary = Slitok.Mint,
    onSecondary = Slitok.OnBrass,
    tertiary = Slitok.Violet,
    background = Slitok.Bg,
    onBackground = Slitok.Fg,
    surface = Slitok.Bg2,
    onSurface = Slitok.Fg,
    surfaceVariant = Slitok.PanelTop,
    onSurfaceVariant = Slitok.Muted,
    error = Slitok.Coral,
    outline = Slitok.Faint,
    outlineVariant = Slitok.Line
)

@Composable
fun BankAggTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SlitokScheme,
        typography = AppTypography,
        content = content
    )
}
