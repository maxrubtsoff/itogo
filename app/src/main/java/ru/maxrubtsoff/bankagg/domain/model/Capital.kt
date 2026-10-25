package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.ZoneId

/** The four parts of total capital, in display order. */
enum class CapitalPart(val key: String) {
    ACCOUNTS("accounts"), DEPOSITS("deposits"), CURRENCY("currency"), CASH("cash");

    companion object {
        fun fromKey(key: String?): CapitalPart? = entries.firstOrNull { it.key == key }
    }
}

/** A non-ruble bank account valued in rubles (no cost basis — no gain). */
data class CurrencyHolding(val account: Account, val valueMinor: Long?)

/** Everything the capital screens show, computed in one place. */
data class CapitalSnapshot(
    val today: LocalDate,
    val parts: List<Part>,
    val totalMinor: Long,
    /** Ruble accounts only; foreign ones are in [currency]. */
    val rubAccounts: List<Account>,
    val activeDeposits: List<Deposit>,
    val depositsAccruedMinor: Long,
    val currency: CurrencySummary,
    val cash: CashBalance?,
    /** When the capital last changed by an event (F-37); null — never. Daily interest is not an event. */
    val lastChangedAt: Long? = null
) {
    data class Part(
        val part: CapitalPart,
        val valueMinor: Long,
        val hasData: Boolean,
        val included: Boolean,
        /** Share of the included total, 0..1 (0 when excluded). */
        val share: Float
    )

    data class CurrencySummary(
        val positions: List<FxPortfolio.Position>,   // наличная валюта
        val holdings: List<CurrencyHolding>,         // валюта на счетах
        val valueMinor: Long,                        // всё в рублях по курсу (без валют без курса)
        val unrealizedMinor: Long,                   // прирост по наличной валюте
        val costMinor: Long,                         // себестоимость наличной валюты с курсом
        val realizedMinor: Long,                     // зафиксировано продажами
        val missingRates: List<String>,              // валюты без курса вообще
        val rateDate: LocalDate?                     // самая старая дата курса среди используемых
    ) {
        val unrealizedPct: Double? get() = if (costMinor > 0L) unrealizedMinor.toDouble() / costMinor else null
        /** Наличные позиции, что есть в списке: с остатком или с зафиксированным результатом. */
        val shownPositions: List<FxPortfolio.Position>
            get() = positions.filter { it.qtyMinor != 0L || it.realizedMinor != 0L }
        /** Сколько разных валют в разделе: наличные и на счетах, каждая один раз. */
        val currencyCount: Int
            get() = (shownPositions.map { it.currency } + holdings.map { it.account.currency }).distinct().size
        val isEmpty: Boolean get() = shownPositions.isEmpty() && holdings.isEmpty()
    }

    fun part(p: CapitalPart): Part = parts.first { it.part == p }
}

/**
 * Total capital from four parts: ruble accounts, active deposits, currency
 * (cash currency + non-ruble accounts at the CBR rate) and cash rubles. The
 * user chooses which parts count ([included]); excluded ones are still
 * computed so the overview can show them greyed out.
 *
 * A deposit that has ended is not counted — its money is already back on an
 * account. A currency with no rate at all is left out and reported in
 * [CapitalSnapshot.CurrencySummary.missingRates].
 */
object CapitalCalculator {

    fun compute(
        accounts: List<Account>,
        deposits: List<Deposit>,
        fxOps: List<FxOperation>,
        rates: List<FxRate>,
        cash: CashBalance?,
        included: Set<CapitalPart>,
        today: LocalDate
    ): CapitalSnapshot {
        val rub = accounts.filter { it.currency == Currencies.RUB }
        val foreign = accounts.filter { it.currency != Currencies.RUB }
        val latest = FxRates.latestByCurrency(rates, today)

        // An account left out by the user (U-18) stays in the list, not in the sums.
        val accountsMinor = rub.filterNot { it.excluded }.sumOf { it.balance.minor }

        val active = deposits.filter { DepositMath.isActive(it, today) }
        val projections = active.map { DepositMath.project(it, today, seriesPoints = 2) }
        val depositsMinor = projections.sumOf { it.currentValue.minor }
        val accruedMinor = projections.sumOf { it.accruedIncome.minor }

        val positions = FxPortfolio.positions(fxOps, latest)
        val holdings = foreign.map { a ->
            CurrencyHolding(a, latest[a.currency]?.let { FxMath.rubOf(a.balance.minor, it.rubPerUnitMicro) })
        }
        val withRate = positions.filter { it.valueMinor != null }
        val missing = (positions.filter { it.valueMinor == null && it.qtyMinor != 0L }.map { it.currency } +
            holdings.filter { it.valueMinor == null && it.account.balance.minor != 0L }.map { it.account.currency })
            .distinct()
        val usedCodes = (positions.filter { it.qtyMinor != 0L }.map { it.currency } + holdings.map { it.account.currency }).toSet()
        val currency = CapitalSnapshot.CurrencySummary(
            positions = positions,
            holdings = holdings,
            valueMinor = withRate.sumOf { it.valueMinor!! } + holdings.filterNot { it.account.excluded }.sumOf { it.valueMinor ?: 0L },
            unrealizedMinor = withRate.sumOf { it.unrealizedMinor!! },
            costMinor = withRate.sumOf { it.costMinor },
            realizedMinor = positions.sumOf { it.realizedMinor },
            missingRates = missing,
            rateDate = usedCodes.mapNotNull { latest[it]?.date }.minOrNull()
        )

        val cashMinor = cash?.amount?.minor ?: 0L

        val raw = listOf(
            Triple(CapitalPart.ACCOUNTS, accountsMinor, rub.isNotEmpty()),
            Triple(CapitalPart.DEPOSITS, depositsMinor, active.isNotEmpty()),
            Triple(CapitalPart.CURRENCY, currency.valueMinor, !currency.isEmpty),
            Triple(CapitalPart.CASH, cashMinor, cash != null && (cashMinor != 0L || cash.updatedAt > 0L))
        )
        val total = raw.filter { it.first in included }.sumOf { it.second }
        // Shares are for the bar: only positive included parts take room.
        val positive = raw.filter { it.first in included }.sumOf { maxOf(0L, it.second) }
        val parts = raw.map { (p, v, has) ->
            val inc = p in included
            CapitalSnapshot.Part(
                part = p,
                valueMinor = v,
                hasData = has || v != 0L,
                included = inc,
                share = if (inc && positive > 0L && v > 0L) v.toFloat() / positive else 0f
            )
        }
        return CapitalSnapshot(
            today = today,
            parts = parts,
            totalMinor = total,
            rubAccounts = rub,
            activeDeposits = active,
            depositsAccruedMinor = accruedMinor,
            currency = currency,
            cash = cash,
            lastChangedAt = lastChangeOf(accounts, cash, fxOps, deposits, today)
        )
    }

    /**
     * The latest event that moved the capital (F-37): an account's balance, the
     * cash, a currency deal, a deposit opened or topped up, or one that ended.
     * The daily accrual of interest is not an event. Null when there is none.
     */
    fun lastChangeOf(
        accounts: List<Account>,
        cash: CashBalance?,
        fxOps: List<FxOperation>,
        deposits: List<Deposit>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val times = accounts.map { it.updatedAt } +
            listOfNotNull(cash?.updatedAt) +
            fxOps.map { it.createdAt } +
            deposits.flatMap { d ->
                listOf(d.createdAt) +
                    d.topUps.filter { !it.date.isAfter(today) }.map { at(it.date) } +
                    (if (d.endDate.isBefore(today)) listOf(at(d.endDate.plusDays(1))) else emptyList())
            }
        return times.filter { it > 0L }.maxOrNull()
    }
}
