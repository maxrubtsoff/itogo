package ru.maxrubtsoff.bankagg.domain.model

/**
 * Colours of well-known services (F-33), bundled with the app — nothing is
 * fetched. A subscription found in the pushes gets its service's own colour
 * instead of a random one: «Okko.tv» is violet, «Netflix» is red.
 */
object ServiceBrands {

    private class Entry(val words: List<String>, val color: Long)

    // Declared before [entries], which is built on first use of the object.
    private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")

    // A longer name goes first, so «яндекс плюс» wins over «яндекс».
    private val entries: List<Entry> = listOf(
        "яндекс плюс" to 0xFFFC3F1D, "яндекс музыка" to 0xFFFC3F1D, "яндекс" to 0xFFFC3F1D, "yandex" to 0xFFFC3F1D,
        "кинопоиск" to 0xFFFF6600, "kinopoisk" to 0xFFFF6600,
        "telegram" to 0xFF2AABEE, "телеграм" to 0xFF2AABEE,
        "vk" to 0xFF0077FF, "вк" to 0xFF0077FF, "вконтакте" to 0xFF0077FF,
        "okko" to 0xFF6B2BD9, "окко" to 0xFF6B2BD9,
        "netflix" to 0xFFE50914,
        "spotify" to 0xFF1DB954,
        "youtube" to 0xFFFF0000, "ютуб" to 0xFFFF0000,
        "wink" to 0xFFFF5A1F, "винк" to 0xFFFF5A1F,
        "ivi" to 0xFFEA003D, "иви" to 0xFFEA003D,
        "мтс" to 0xFFE30611, "mts" to 0xFFE30611,
        "сберпрайм" to 0xFF21A038, "сбер" to 0xFF21A038,
        "apple" to 0xFF3A3F52, "icloud" to 0xFF3A3F52,
        "google" to 0xFF4285F4,
        "ozon" to 0xFF005BFF, "озон" to 0xFF005BFF,
        "megogo" to 0xFFFF3F6C
    ).map { (name, color) -> Entry(words(name), color) }
        .sortedByDescending { it.words.size }

    /** The service's own colour, or null when the name isn't a known service. */
    fun known(name: String): Long? {
        val title = words(name)
        if (title.isEmpty()) return null
        return entries.firstOrNull { e -> contains(title, e.words) }?.color
    }

    /** The service's colour when known, otherwise a steady colour per name. */
    fun colorFor(name: String): Long = known(name) ?: CbrBanks.colorFor(norm(name))

    /** The alias's words follow each other in the title; a short word must match whole, a long one by its start. */
    private fun contains(title: List<String>, alias: List<String>): Boolean {
        if (alias.size > title.size) return false
        return (0..title.size - alias.size).any { from ->
            alias.indices.all { i ->
                val t = title[from + i]
                val a = alias[i]
                t == a || (a.length >= LONG_WORD && t.startsWith(a))
            }
        }
    }

    /** Lower case, «ё» as «е», words split on anything that isn't a letter or a digit. */
    private fun words(s: String): List<String> =
        norm(s).split(NOT_WORD).filter { it.isNotEmpty() }

    private fun norm(s: String): String = s.lowercase().replace('ё', 'е').trim()

    private const val LONG_WORD = 5
}
