package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/** What a bank's card lists, in the order it lists it (U-52). */
enum class BankHolding { ACCOUNTS, DEPOSITS, CASHBACK, SUBSCRIPTIONS }

/**
 * What one bank holds, for its card (U-24): rouble accounts, open deposits,
 * this month's cashback categories and active subscriptions.
 */
data class BankSummary(
    val accounts: Money,
    val accountCount: Int,
    val deposits: Money,
    val depositCount: Int,
    val cashbackCount: Int,
    val subscriptionCount: Int = 0,
    val subscriptionsPerMonth: Money = Money.ZERO
) {
    /**
     * Subscriptions don't count here: they live on «Платежи» whatever banks are
     * kept, so taking a bank out never touches them.
     */
    val isEmpty: Boolean get() = accountCount == 0 && depositCount == 0 && cashbackCount == 0

    /** U-48: taking out a bank with nothing in it needs no «Вернуть» — adding it back is one tap. */
    val offersUndo: Boolean get() = !isEmpty

    /** The rows of the card: what the bank holds, and only of the sections the user keeps. */
    fun holdings(features: Features): List<BankHolding> = buildList {
        if (features.has(Feature.ACCOUNTS) && accountCount > 0) add(BankHolding.ACCOUNTS)
        if (features.has(Feature.DEPOSITS) && depositCount > 0) add(BankHolding.DEPOSITS)
        if (features.has(Feature.CASHBACK) && cashbackCount > 0) add(BankHolding.CASHBACK)
        if (features.showSubscriptions && subscriptionCount > 0) add(BankHolding.SUBSCRIPTIONS)
    }

    companion object {
        fun of(
            bankKey: String,
            accounts: List<Account>,
            deposits: List<Deposit>,
            monthCashback: List<CashbackItem>,
            subscriptions: List<Subscription>,
            today: LocalDate
        ): BankSummary {
            val acc = accounts.filter { it.brand.key == bankKey && it.currency == Currencies.RUB }
            val dep = deposits.filter { it.brand.key == bankKey && !it.endDate.isBefore(today) }
            val subs = subscriptions.filter { it.brand?.key == bankKey && it.status == SubscriptionStatus.ACTIVE }
            return BankSummary(
                accounts = Money.ofMinor(acc.sumOf { it.balance.minor }),
                accountCount = acc.size,
                deposits = Money.ofMinor(dep.sumOf { it.investedTotal.minor }),
                depositCount = dep.size,
                cashbackCount = monthCashback.count { it.brand.key == bankKey },
                subscriptionCount = subs.size,
                subscriptionsPerMonth = Money.ofMinor(subs.sumOf { it.period.perMonth(it.priceMinor) })
            )
        }
    }
}
