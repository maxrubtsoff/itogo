package ru.maxrubtsoff.bankagg.domain.model

/**
 * Widths of the capital composition bar (B-9): every non-zero part gets at
 * least [MIN_SHARE] of the bar so a tiny one doesn't look like a rendering
 * glitch; the rest shrink in proportion. The figures in the rows stay exact —
 * this is only how the bar is drawn.
 */
object CapitalBar {
    const val MIN_SHARE = 0.04f

    /** [shares] — parts' real shares (any scale); returns widths summing to 1, same order. Zero stays zero. */
    fun widths(shares: List<Float>, minShare: Float = MIN_SHARE): List<Float> {
        val total = shares.filter { it > 0f }.sum()
        if (total <= 0f) return shares.map { 0f }
        val real = shares.map { if (it > 0f) it / total else 0f }
        val nonZero = real.count { it > 0f }
        if (nonZero * minShare >= 1f) return real.map { if (it > 0f) 1f / nonZero else 0f }
        // Lifting a part can push another below the minimum: repeat until stable.
        val lifted = BooleanArray(real.size)
        while (true) {
            val liftedSum = lifted.count { it } * minShare
            val restReal = real.indices.filter { !lifted[it] }.sumOf { real[it].toDouble() }.toFloat()
            val scale = (1f - liftedSum) / restReal
            val newly = real.indices.filter { !lifted[it] && real[it] > 0f && real[it] * scale < minShare }
            if (newly.isEmpty()) {
                return real.indices.map { i -> if (lifted[i]) minShare else real[i] * scale }
            }
            newly.forEach { lifted[it] = true }
        }
    }
}
