package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/**
 * The home cashback card, decided by the app — no "favourites" to tick.
 * Rows are catalogue groups: whatever each bank calls it ("Отели", "СберТревел"),
 * the card says "Путешествия" once ([CashbackSummary.rowKey]).
 *
 * Every group has a fixed "everyday" weight (groceries matter more than travel).
 * Each category's best percent this month gets `score = weight × percent`; the
 * top four become the main rows. The month's highest rate, if it is ≥ 10% and
 * not already shown, gets one "максимум" row (brands included: "Lamoda 15%").
 * "Базовый кэшбэк" / "Все покупки" never compete: they are each bank's base
 * rate, always shown apart ([Spotlight.base]).
 */
object CashbackSpotlight {

    const val MAIN_ROWS = 4
    const val MAX_THRESHOLD = 10.0
    const val MAX_BANKS = 3

    /** Brand offers (a shop, not a category) weigh like "Другое". */
    private const val BRAND_WEIGHT = 0.2

    private val WEIGHTS: Map<String, Double> = mapOf(
        "groceries" to 1.0,
        "cafe" to 0.9, "fuel" to 0.9,
        "pharmacy" to 0.8, "online" to 0.8,
        "restaurant" to 0.7, "taxi" to 0.7,
        "transport" to 0.6,
        "mobile" to 0.5, "health" to 0.5, "clothing" to 0.5,
        "beauty" to 0.4, "home" to 0.4, "electronics" to 0.4, "pets" to 0.4,
        "kids" to 0.4, "sport" to 0.4, "entertainment" to 0.4,
        "travel" to 0.3,
        CategoryGroups.OTHER to 0.2
    )

    /** Groups that mean "any purchase": a bank's base rate, never a main row. */
    val CATCH_ALL = setOf("base", "all")

    fun isBase(item: CashbackItem): Boolean = item.groupKey in CATCH_ALL

    fun weightOf(groupKey: String): Double = WEIGHTS[groupKey] ?: BRAND_WEIGHT

    data class Row(
        val title: String,
        val iconKey: String,
        val groupKey: String,
        val percent: Double,
        val banks: List<BankBrand>,   // capped to [MAX_BANKS]
        val totalBanks: Int,
        val score: Double
    ) {
        val extraBanks: Int get() = totalBanks - banks.size
    }

    /** Banks sharing one base percent: "1% · Альфа, Т-Банк". */
    data class BaseGroup(val percent: Double, val banks: List<BankBrand>)

    data class Spotlight(
        val monthKey: String,
        val rows: List<Row>,
        /** The month's top rate when it is ≥ 10% and not among [rows]. */
        val maximum: Row?,
        /** Each bank's base cashback, grouped by percent, highest first. */
        val base: List<BaseGroup>,
        /** Every regular category, most useful first (the widget fills its space from here). */
        val ranked: List<Row>,
        /** Nothing added for this month yet. */
        val missingThisMonth: Boolean,
        /** From the 25th, when next month has nothing yet. */
        val offerNextMonth: Boolean,
        val nextMonthKey: String
    ) {
        /** Every row in display order: main rows, the maximum, then the rest (for the widget). */
        val allRows: List<Row> get() = rows + listOfNotNull(maximum) + ranked.filter { it !in rows && it != maximum }
    }

    fun build(items: List<CashbackItem>, hasNextMonth: Boolean, today: LocalDate): Spotlight {
        val monthKey = CashbackMonth.keyOf(today)
        val candidates = CashbackSummary.byCategory(items).map { r ->
            // Rows are catalogue groups (named from the catalogue) or, outside it, a bank's own text.
            val weight = if (r.groupKey != CategoryGroups.OTHER) weightOf(r.groupKey) else BRAND_WEIGHT
            Row(
                title = r.category,
                iconKey = r.iconKey,
                groupKey = r.groupKey,
                percent = r.best,
                banks = r.bestBanks.take(MAX_BANKS),
                totalBanks = r.bestBanks.size,
                score = weight * r.best
            )
        }
        val regular = candidates.filter { it.groupKey !in CATCH_ALL }
        val ranked = regular.sortedWith(
            compareByDescending<Row> { it.score }
                .thenByDescending { it.percent }
                .thenBy { it.title.lowercase() }
        )
        val main = ranked.take(MAIN_ROWS)
        val maximum = regular
            .filter { it.percent >= MAX_THRESHOLD && it !in main }
            .maxWithOrNull(compareBy<Row> { it.percent }.thenBy { it.score })
            ?.takeIf { top -> main.none { it.percent >= top.percent } }

        return Spotlight(
            monthKey = monthKey,
            rows = main,
            maximum = maximum,
            base = baseGroups(items),
            ranked = ranked,
            missingThisMonth = items.isEmpty(),
            offerNextMonth = CashbackMonth.shouldOfferNextMonth(today) && !hasNextMonth,
            nextMonthKey = CashbackMonth.nextKey(monthKey)
        )
    }

    /**
     * Each bank's base rate (its best "base"/"all" offer), grouped by percent:
     * Т-Банк 3% and Альфа, Сбер 1% → [3%: Т-Банк], [1%: Альфа, Сбер].
     */
    fun baseGroups(items: List<CashbackItem>): List<BaseGroup> =
        items.filter(::isBase)
            .groupBy { it.brand }
            .map { (brand, list) -> brand to list.maxOf { it.percent } }
            .groupBy({ it.second }, { it.first })
            .map { (percent, banks) -> BaseGroup(percent, banks.sortedWith(compareBy({ it.order }, { it.title }))) }
            .sortedByDescending { it.percent }
}
