package ru.maxrubtsoff.bankagg.domain.model

/**
 * Cutting a logo out of a picture (F-22): logos usually sit on a plain field, so
 * when the four corners agree on a colour, that colour is the background and
 * goes transparent. Colours are ARGB ints.
 */
object LogoBackground {

    /** The background colour if all [corners] are within [tolerance] of each other, else null. */
    fun detect(corners: List<Int>, tolerance: Int = 40): Int? {
        if (corners.isEmpty()) return null
        val first = corners.first()
        return if (corners.all { near(it, first, tolerance) }) first else null
    }

    /** Close enough per channel (alpha ignored). */
    fun near(a: Int, b: Int, tolerance: Int = 40): Boolean =
        kotlin.math.abs(r(a) - r(b)) <= tolerance &&
            kotlin.math.abs(g(a) - g(b)) <= tolerance &&
            kotlin.math.abs(bl(a) - bl(b)) <= tolerance

    /** Every pixel near [background] becomes fully transparent; others stay as they are. */
    fun clear(pixels: IntArray, background: Int, tolerance: Int = 40): IntArray =
        IntArray(pixels.size) { i -> if (near(pixels[i], background, tolerance)) 0 else pixels[i] }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun bl(c: Int) = c and 0xFF
}
