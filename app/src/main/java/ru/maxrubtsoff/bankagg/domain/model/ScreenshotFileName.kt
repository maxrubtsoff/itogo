package ru.maxrubtsoff.bankagg.domain.model

/**
 * Guesses the bank from a screenshot's file name. Some launchers append the
 * foreground app: Samsung One UI adds its label
 * ("Screenshot_20261020_141742_Ozon.jpg"), MIUI adds the package
 * ("…_ru.sberbankmobile.jpg"). Stock Android adds nothing → null.
 */
object ScreenshotFileName {

    /** App labels as Samsung writes them (normalised by [CategoryMatcher.normalize]). */
    private val LABELS: Map<String, BankBrand> = mapOf(
        "ozon" to BankBrand.OZON,
        "t bank" to BankBrand.TBANK,
        "yandex pay" to BankBrand.YANDEX
    )

    private val EXTENSION = Regex("""\.[A-Za-z0-9]{2,5}$""")
    private val COPY_SUFFIX = Regex("""\s*\(\d+\)$""")
    private val TIMESTAMP_PREFIX = Regex("""^.*?\d{8}_\d{6}_""")

    fun guessBank(fileName: String?): BankBrand? {
        if (fileName.isNullOrBlank()) return null
        val base = fileName.trim().replace(EXTENSION, "").replace(COPY_SUFFIX, "")

        BankBrand.catalogue
            .flatMap { b -> b.packages.map { it to b } }
            .sortedByDescending { it.first.length }
            .firstOrNull { base.contains(it.first) }
            ?.let { return it.second }

        if (!TIMESTAMP_PREFIX.containsMatchIn(base)) return null
        val label = CategoryMatcher.normalize(base.replace(TIMESTAMP_PREFIX, ""))
        return LABELS[label]
    }
}
