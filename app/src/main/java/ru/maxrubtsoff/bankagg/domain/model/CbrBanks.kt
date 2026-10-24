package ru.maxrubtsoff.bankagg.domain.model

/**
 * The Bank of Russia's list of active credit institutions (F-22), bundled with
 * the app and updated with new versions — the app never fetches it. Finding a
 * bank here gives it one correct name instead of «Россельхоз» / «РСХБ» / …
 */
object CbrBanks {

    data class Entry(val regNumber: Int, val name: String)

    /** "1481\tСбербанк" lines; '#' starts a comment. */
    fun parse(text: String): List<Entry> =
        text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                val rn = line.substring(0, tab.coerceAtLeast(0)).trim().toIntOrNull() ?: return@mapNotNull null
                Entry(rn, line.substring(tab + 1).trim())
            }
            .toList()

    /** The entry as a bank: a known one (with its app and colour) when we know it. */
    fun toBank(e: Entry): BankBrand =
        BankBrand.fromRegNumber(e.regNumber)
            ?: BankBrand("${BankBrand.CBR_PREFIX}${e.regNumber}", e.name, colorFor(e.name), regNumber = e.regNumber)

    /**
     * Banks whose name (or a known bank's alias) has a word starting with [query]:
     * «россе» → Россельхозбанк. Known banks first (largest first), then А→Я.
     * Case and «ё» don't matter.
     */
    fun search(query: String, entries: List<Entry>, limit: Int = 20): List<BankBrand> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val fromKnown = BankBrand.known.filter { b -> matches(b.title, q) || b.aliases.any { matches(it, q) } }
        val fromCbr = entries.filter { matches(it.name, q) }.map(::toBank)
        return (fromKnown + fromCbr)
            .distinctBy { it.key }
            .sortedWith(compareBy<BankBrand>({ it.order }, { it.title.lowercase() }))
            .take(limit)
    }

    private fun matches(name: String, q: String): Boolean {
        val n = norm(name)
        return n.startsWith(q) || n.split(' ', '-', '.').any { it.startsWith(q) }
    }

    private fun norm(s: String): String = s.lowercase().replace('ё', 'е').replace('"', ' ').trim()

    /** A steady colour per name from a calm palette, so a bank never changes colour. */
    fun colorFor(name: String): Long = PALETTE[Math.floorMod(name.lowercase().hashCode(), PALETTE.size)]

    private val PALETTE = listOf(
        0xFF2D5BA8, 0xFF1F7A5A, 0xFF8C3B5E, 0xFF6A4FB5, 0xFFB5652A, 0xFF2F7D8C, 0xFF7A6A2A, 0xFF4A5568
    )
}
