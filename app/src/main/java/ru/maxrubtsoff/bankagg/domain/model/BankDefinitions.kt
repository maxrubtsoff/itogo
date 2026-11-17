package ru.maxrubtsoff.bankagg.domain.model

/** Which bank looks are kept on the phone (B-49, B-130). */
object BankDefinitions {

    /**
     * Everything the user may have changed is stored, a known bank included: its
     * picture and letters would otherwise be lost on the next read (B-49).
     * BankBrand.equals is by key only, so the look itself is compared. A placeholder
     * (a key the directory didn't know yet) is never stored (B-130).
     */
    fun persistable(banks: List<BankBrand>, known: List<BankBrand>): List<BankBrand> =
        banks.filter { b ->
            if (b.isPlaceholder) return@filter false
            val k = known.firstOrNull { it.key == b.key }
            k == null || b.logoFile != null || b.letters != k.letters ||
                b.primaryColor != k.primaryColor || b.textColor != k.textColor || b.title != k.title ||
                b.packages != k.packages
        }

    /** [stored] definitions updated with [banks]; a placeholder never overwrites a stored definition. */
    fun merge(stored: List<BankBrand>, banks: List<BankBrand>, known: List<BankBrand>): List<BankBrand> =
        (stored.associateBy { it.key } + persistable(banks, known).associateBy { it.key }).values.toList()
}
