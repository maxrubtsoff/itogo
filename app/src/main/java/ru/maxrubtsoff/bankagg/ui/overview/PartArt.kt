package ru.maxrubtsoff.bankagg.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart

/**
 * Soft "3D" pictures of the capital parts in the Слиток palette: rounded
 * shapes, light from the top left, a highlight and a soft shadow. Drawn in a
 * 92×70 box and scaled — vector, offline, no image files.
 */
@Composable
fun CapitalPartArt(part: CapitalPart, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = minOf(size.width / 92f, size.height / 70f)
        val dx = (size.width - 92f * k) / 2f
        val dy = (size.height - 70f * k) / 2f
        translateScaled(dx, dy, k) {
            shadow()
            when (part) {
                CapitalPart.ACCOUNTS -> card()
                CapitalPart.DEPOSITS -> safe()
                CapitalPart.CURRENCY -> coins()
                CapitalPart.CASH -> cash()
            }
        }
    }
}

private inline fun DrawScope.translateScaled(dx: Float, dy: Float, k: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, dy)
    scale(k, k, pivot = Offset.Zero) { block() }
    drawContext.transform.translate(-dx, -dy)
}

private fun DrawScope.shadow() =
    drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(16f, 58.5f), size = Size(60f, 9f))

private fun DrawScope.rounded(color: Brush, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.rounded(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun diagonal(stops: List<Color>, x: Float, y: Float, w: Float, h: Float) =
    Brush.linearGradient(stops, start = Offset(x, y), end = Offset(x + w, y + h))

/** Счета: a brass card over a dark one, with a chip. */
private fun DrawScope.card() {
    rotate(-8f, pivot = Offset(46f, 36f)) {
        rounded(diagonal(listOf(Color(0xFF3A3F4A), Color(0xFF1A1E26)), x = 18f, y = 14f, w = 58f, h = 38f), 18f, 14f, 58f, 38f, 12f)
    }
    rotate(5f, pivot = Offset(46f, 38f)) {
        rounded(diagonal(listOf(Color(0xFFF7DA98), Color(0xFFD7A74A), Color(0xFF7A5A20)), x = 14f, y = 18f, w = 64f, h = 40f), 14f, 18f, 64f, 40f, 12f)
        rounded(Color(0xFFFFF3D1).copy(alpha = 0.9f), 22f, 28f, 13f, 10f, 4f)
        rounded(Color(0xFF5A4214).copy(alpha = 0.45f), 22f, 46f, 26f, 4f, 2f)
        drawCircle(Color(0xFFFFF3D1).copy(alpha = 0.55f), 4f, Offset(66f, 48f))
        rounded(Color.White.copy(alpha = 0.18f), 18f, 20f, 56f, 12f, 6f)
    }
}

/** Вклады: a soft violet safe with a brass dial and handle. */
private fun DrawScope.safe() {
    rounded(Brush.horizontalGradient(listOf(Color(0xFF5A4AB0), Color(0xFF2E2468)), startX = 22f, endX = 72f), 22f, 9f, 50f, 52f, 16f)
    rounded(diagonal(listOf(Color(0xFFCFC2FF), Color(0xFF9A86F5), Color(0xFF4E3FA0)), x = 20f, y = 7f, w = 50f, h = 50f), 20f, 7f, 50f, 50f, 16f)
    drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(26f, 13f), Size(38f, 38f), CornerRadius(11f, 11f), style = Stroke(1.5f))
    rounded(Color.White.copy(alpha = 0.18f), 24f, 10f, 40f, 10f, 5f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3D1), Color(0xFFD7A74A), Color(0xFF7A5A20)), center = Offset(41.5f, 28.5f), radius = 14f), 10f, Offset(45f, 32f))
    drawCircle(Color(0xFF5A4214), 3.5f, Offset(45f, 32f))
    rounded(Color(0xFFF0C878), 60f, 28f, 4f, 9f, 2f)
}

/** Валюта: two round coins face on. */
private fun DrawScope.coins() {
    val face = { c: Offset, r: Float ->
        Brush.linearGradient(listOf(Color(0xFFD6F1FF), Color(0xFF6CC4E8), Color(0xFF2A6E8C)), start = c - Offset(r, r), end = c + Offset(r, r))
    }
    val edge = Brush.verticalGradient(listOf(Color(0xFF3C88A8), Color(0xFF1E4E64)))
    drawCircle(edge, 18f, Offset(60f, 28f))
    drawCircle(face(Offset(58f, 26f), 18f), 18f, Offset(58f, 26f), alpha = 0.8f)
    drawCircle(Color.White.copy(alpha = 0.35f), 12.5f, Offset(58f, 26f), style = Stroke(1.5f))
    drawCircle(edge, 21f, Offset(38f, 40f))
    drawCircle(face(Offset(36f, 38f), 21f), 21f, Offset(36f, 38f))
    drawCircle(Color.White.copy(alpha = 0.45f), 15f, Offset(36f, 38f), style = Stroke(1.5f))
    rotate(-30f, pivot = Offset(29f, 28f)) {
        drawOval(Color.White.copy(alpha = 0.35f), Offset(21f, 24f), Size(16f, 8f))
    }
}

/** Наличные: a rounded stack of notes with a flat brass band. */
private fun DrawScope.cash() {
    rotate(-6f, pivot = Offset(46f, 38f)) {
        rounded(Color(0xFF6E5E44), 14f, 26f, 64f, 32f, 10f)
        rounded(Color(0xFF9C8A6C), 14f, 21f, 64f, 32f, 10f)
        rounded(diagonal(listOf(Color(0xFFF3E9D6), Color(0xFFCBB89A), Color(0xFF7C6A4E)), x = 14f, y = 16f, w = 64f, h = 32f), 14f, 16f, 64f, 32f, 10f)
        drawRoundRect(Color(0xFF7C6A4E).copy(alpha = 0.45f), Offset(19f, 21f), Size(54f, 22f), CornerRadius(7f, 7f), style = Stroke(1.2f))
        drawCircle(Color(0xFF7C6A4E).copy(alpha = 0.6f), 7f, Offset(46f, 32f), style = Stroke(1.5f))
        rounded(Color.White.copy(alpha = 0.22f), 18f, 18f, 56f, 8f, 4f)
        rounded(Color(0xFFA8803A), 56f, 48f, 10f, 10f, 0f)
        rounded(Brush.horizontalGradient(listOf(Color(0xFFE8C27A), Color(0xFFD7A74A), Color(0xFFB48A3E)), startX = 56f, endX = 66f), 56f, 16f, 10f, 32f, 0f)
    }
}

/** The «Платежи» sections, drawn in the same 3D manner as the capital parts (F-30). */
enum class PaymentArt { CASHBACK, SUBSCRIPTIONS }

@Composable
fun PaymentPartArt(kind: PaymentArt, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = minOf(size.width / 92f, size.height / 70f)
        val dx = (size.width - 92f * k) / 2f
        val dy = (size.height - 70f * k) / 2f
        translateScaled(dx, dy, k) {
            shadow()
            when (kind) {
                PaymentArt.CASHBACK -> percentCoin()
                PaymentArt.SUBSCRIPTIONS -> repeatCard()
            }
        }
    }
}

/** Кэшбэк: a brass coin with a percent sign (as in the mock-up). */
private fun DrawScope.percentCoin() {
    drawCircle(Color(0xFF8A6324), 25f, Offset(46f, 36f))
    drawCircle(diagonal(listOf(Color(0xFFF6DDA0), Color(0xFFD7A74A), Color(0xFF9C7224)), x = 21f, y = 8f, w = 50f, h = 50f), 25f, Offset(46f, 33f))
    drawCircle(Color(0xFF14120E).copy(alpha = 0.35f), 18f, Offset(46f, 33f), style = Stroke(1.6f))
    drawLine(Color(0xFF14120E), Offset(38f, 42f), Offset(54f, 24f), strokeWidth = 3.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(Color(0xFF14120E), 3.2f, Offset(39f, 26f))
    drawCircle(Color(0xFF14120E), 3.2f, Offset(53f, 40f))
    drawOval(Color.White.copy(alpha = 0.28f), topLeft = Offset(30f, 12f), size = Size(22f, 8f))
}

/** Подписки: a mint calendar card with a repeat arrow. */
private fun DrawScope.repeatCard() {
    rounded(Color(0xFF13704A), 20f, 14f, 52f, 44f, 10f)
    rounded(diagonal(listOf(Color(0xFF9BF2CB), Color(0xFF59E2A6), Color(0xFF1F9C68)), x = 20f, y = 10f, w = 52f, h = 44f), 20f, 10f, 52f, 44f, 10f)
    rounded(Color(0xFF0E5B3C).copy(alpha = 0.55f), 20f, 10f, 52f, 11f, 6f)
    drawArc(Color(0xFF0B3B27), startAngle = 200f, sweepAngle = 270f, useCenter = false, topLeft = Offset(36f, 26f), size = Size(20f, 20f), style = Stroke(3f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    val tip = Offset(37.5f, 32f)
    drawLine(Color(0xFF0B3B27), tip, Offset(36f, 26.5f), strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(Color(0xFF0B3B27), tip, Offset(42.5f, 30f), strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}
