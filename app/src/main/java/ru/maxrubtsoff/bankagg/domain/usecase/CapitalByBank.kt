package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSlice
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.SubscriptionStatus

/**
 * The capital cut by bank (F-53): a bank's sum is its accounts (foreign ones at
 * the rate) plus its deposits, only from the parts counted in the total. Banks
 * with nothing are left out, the biggest comes first, and what no bank holds is
 * the last row «не в банке» — so the rows add up to the total.
 */
object CapitalByBank {

    fun slice(
        snap: CapitalSnapshot,
        subscriptions: List<Subscription>,
        cashback: List<CashbackItem>
    ): List<BankSlice> {
        fun counted(p: CapitalPart) = snap.parts.firstOrNull { it.part == p }?.included == true

        val accounts = buildList {
            if (counted(CapitalPart.ACCOUNTS)) snap.rubAccounts.filterNot { it.excluded }
                .forEach { add(it.brand to BankSlice.Line(it.title, it.balance.minor)) }
            if (counted(CapitalPart.CURRENCY)) snap.currency.holdings.filterNot { it.account.excluded }
                .forEach { h -> h.valueMinor?.let { add(h.account.brand to BankSlice.Line(h.account.title, it)) } }
        }
        val deposits = if (!counted(CapitalPart.DEPOSITS)) emptyList() else snap.activeDeposits.map {
            it.brand to BankSlice.Line(it.title ?: "Вклад", DepositMath.project(it, snap.today, seriesPoints = 2).currentValue.minor)
        }

        val named = (accounts.map { it.first } + deposits.map { it.first })
            .filter { it.key != BankBrand.UNKNOWN.key }
            .distinctBy { it.key }
        val activeSubs = subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }

        val banks = named.map { brand ->
            val a = accounts.filter { it.first.key == brand.key }.map { it.second }
            val d = deposits.filter { it.first.key == brand.key }.map { it.second }
            BankSlice(
                brand = brand,
                valueMinor = a.sumOf { it.valueMinor } + d.sumOf { it.valueMinor },
                share = 0f,
                accounts = a,
                deposits = d,
                hasSubscriptions = activeSubs.any { it.brand?.key == brand.key },
                hasCashback = cashback.any { it.brand.key == brand.key }
            )
        }.filter { it.valueMinor != 0L }.sortedByDescending { it.valueMinor }

        val rest = snap.totalMinor - banks.sumOf { it.valueMinor }
        val all = banks + (if (rest != 0L) listOf(BankSlice(null, rest, 0f)) else emptyList())
        val positive = all.sumOf { maxOf(0L, it.valueMinor) }
        return all.map { it.copy(share = if (positive > 0L && it.valueMinor > 0L) it.valueMinor.toFloat() / positive else 0f) }
    }

    /** The switch «По частям · По банкам» shows only when there is something to compare. */
    fun worthShowing(slices: List<BankSlice>): Boolean = slices.count { it.brand != null } >= 2
}
