package ru.maxrubtsoff.bankagg.domain.model

/**
 * How a bank or a subscription looks (F-33): up to three [letters] on [colorArgb],
 * or the user's own picture ([logoFile], a file in the app's storage). A null
 * [textColorArgb] means the letters are black or white, whichever reads on the
 * colour. One look for both, so one editor and one avatar draw them.
 */
data class BrandDecor(
    val colorArgb: Long,
    val letters: String,
    val textColorArgb: Long? = null,
    val logoFile: String? = null
) {
    companion object {
        /** The look a name gets by itself: its initials, a known service's colour or a steady one. */
        fun defaultFor(name: String): BrandDecor =
            BrandDecor(ServiceBrands.colorFor(name), BankBrand.lettersOf(name.trim()))
    }
}
