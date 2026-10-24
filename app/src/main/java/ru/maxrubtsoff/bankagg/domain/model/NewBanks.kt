package ru.maxrubtsoff.bankagg.domain.model

/**
 * An app that isn't among the user's banks but posted something that reads like
 * a bank operation (F-23). Its last notification is kept only until the user
 * answers: "this is my bank X", "a new bank", or "not a bank".
 */
data class BankCandidate(
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String,
    val postedAt: Long,
    /** The user's bank this most likely is (asked as «Это …?»), or a known bank to add. */
    val guessKey: String?
)

object NewBanks {

    sealed interface Decision {
        data object Skip : Decision
        data class Ask(val guessKey: String?) : Decision
    }

    /**
     * What to do with a notification from [pkg]. Never creates anything by itself:
     * the most it does is ask. Skips the user's own banks (a bank whose bell is
     * off stays quiet), apps marked "not a bank", and anything that doesn't read
     * as a bank operation ([BankSignal], B-25).
     */
    fun decide(
        pkg: String,
        appName: String,
        title: String?,
        text: String,
        mine: List<BankBrand>,
        ignored: Set<String>,
        ownApp: String
    ): Decision {
        if (pkg == ownApp || pkg in ignored) return Decision.Skip
        if (mine.any { pkg in it.packages }) return Decision.Skip
        if (!BankSignal.looksLikeBank(pkg, title, text)) return Decision.Skip
        return Decision.Ask(guess(pkg, appName, title, mine))
    }

    /** A bank the user added by hand whose name matches the app; else the known bank owning the app. */
    fun guess(pkg: String, appName: String, title: String?, mine: List<BankBrand>): String? {
        val names = listOfNotNull(appName, title).map(::norm)
        val byName = mine.filter { it.packages.isEmpty() }.firstOrNull { b ->
            val bankNames = (listOf(b.title) + b.aliases).map(::norm)
            names.any { n -> bankNames.any { similar(n, it) } }
        }
        if (byName != null) return byName.key
        return BankBrand.known.firstOrNull { pkg in it.packages }?.key
    }

    /** Same name, one inside the other, or a shared meaningful word ("BSB Bank" ~ "BSB"). */
    fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b || a.contains(b) || b.contains(a)) return true
        val wa = words(a)
        return words(b).any { it in wa }
    }

    private fun words(s: String): Set<String> =
        s.split(' ', '-', '.').filter { it.length >= 3 && it !in GENERIC }.toSet()

    private fun norm(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[\"«»]"), " ").trim()

    private val GENERIC = setOf("банк", "bank", "онлайн", "online", "мобайл", "mobile", "app")
}
