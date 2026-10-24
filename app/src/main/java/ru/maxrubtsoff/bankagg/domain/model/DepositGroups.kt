package ru.maxrubtsoff.bankagg.domain.model

/**
 * Deposits grouped by bank, for the "по банкам" order: how much money sits in
 * each bank. Groups go from the largest sum (reversed: smallest); inside a group
 * deposits end soonest first.
 */
object DepositGroups {

    data class BankGroup<T>(
        val brand: BankBrand,
        val items: List<T>,
        val value: Money,     // сейчас на вкладах этого банка
        val accrued: Money    // доход по банку к сегодня
    )

    fun <T> byBank(
        items: List<T>,
        deposit: (T) -> Deposit,
        projection: (T) -> DepositMath.Projection,
        reversed: Boolean = false
    ): List<BankGroup<T>> {
        val groups = items.groupBy { deposit(it).brand }.map { (brand, list) ->
            BankGroup(
                brand = brand,
                items = list.sortedWith(compareBy<T> { deposit(it).endDate }.thenBy { deposit(it).id }),
                value = Money.ofMinor(list.sumOf { projection(it).currentValue.minor }),
                accrued = Money.ofMinor(list.sumOf { projection(it).accruedIncome.minor })
            )
        }
        val bySum = compareByDescending<BankGroup<T>> { it.value.minor }
        val primary = if (reversed) bySum.reversed() else bySum
        return groups.sortedWith(primary.thenBy { it.brand.title.lowercase() })
    }
}
