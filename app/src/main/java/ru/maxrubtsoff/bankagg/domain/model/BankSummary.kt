package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/**
 * What one bank holds, for its card (U-24): rouble accounts, open deposits and
 * this month's cashback categories.
 */
data class BankSummary(
    val accounts: Money,
    val accountCount: Int,
    val deposits: Money,
    val depositCount: Int,
    val cashbackCount: Int
) {
    val isEmpty: Boolean get() = accountCount == 0 && depositCount == 0 && cashbackCount == 0

    /** U-48: taking out a bank with nothing in it needs no «Вернуть» — adding it back is one tap. */
    val offersUndo: Boolean get() = !isEmpty

    companion object {
        fun of(
            bankKey: String,
            accounts: List<Account>,
            deposits: List<Deposit>,
            monthCashback: List<CashbackItem>,
            today: LocalDate
        ): BankSummary {
            val acc = accounts.filter { it.brand.key == bankKey && it.currency == Currencies.RUB }
            val dep = deposits.filter { it.brand.key == bankKey && !it.endDate.isBefore(today) }
            return BankSummary(
                accounts = Money.ofMinor(acc.sumOf { it.balance.minor }),
                accountCount = acc.size,
                deposits = Money.ofMinor(dep.sumOf { it.investedTotal.minor }),
                depositCount = dep.size,
                cashbackCount = monthCashback.count { it.brand.key == bankKey }
            )
        }
    }
}
