package ru.maxrubtsoff.bankagg.domain.model

/**
 * U-66: is a recognised screen a deposit that is already saved? Strict: the bank, the
 * amount, the rate and both dates must all be read and equal. A field that wasn't
 * read means «not sure» — and not sure is never a duplicate.
 */
object DepositMatch {

    fun find(brand: BankBrand?, draft: DepositDraft, existing: List<Deposit>): Deposit? {
        brand ?: return null
        val principal = draft.principal ?: return null
        val rate = draft.rateBp ?: return null
        val start = draft.startDate ?: return null
        val end = draft.endDate ?: return null
        return existing.firstOrNull {
            it.brand.key == brand.key && it.principal == principal && it.rateBp == rate &&
                it.startDate == start && it.endDate == end
        }
    }
}
