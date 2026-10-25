package ru.maxrubtsoff.bankagg.benchmark

import java.time.LocalDate
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.PeriodUnit
import ru.maxrubtsoff.bankagg.domain.model.Subscription

/**
 * U-55: a full phone for Macrobenchmark — 10 banks, 30 accounts, 15 deposits,
 * 6 months of cashback, 20 subscriptions. Built from constants and [today]
 * only, so every run measures the same data. Amounts are [Money] in minor units.
 * Only the benchmark build carries it ([BenchmarkSeedReceiver]).
 */
object BenchmarkData {

    val banks: List<BankBrand> = listOf(
        BankBrand.SBER, BankBrand.VTB, BankBrand.GAZPROM, BankBrand.ALFA, BankBrand.RSHB,
        BankBrand.MKB, BankBrand.SOVCOM, BankBrand.TBANK, BankBrand.RAIF, BankBrand.OZON
    )

    /** One bank, one currency: the app keeps accounts as `bank:CUR`, so 10 banks × 3 currencies = 30. */
    val currencies = listOf("RUB", "USD", "EUR")

    class AccountSeed(val id: String, val brand: BankBrand, val currency: String, val balanceMinor: Long)

    fun accounts(): List<AccountSeed> = banks.flatMapIndexed { b, brand ->
        currencies.mapIndexed { c, cur ->
            val base = if (cur == "RUB") 8_500_000L else 120_000L
            AccountSeed("${brand.key}:$cur", brand, cur, base + (b * 7 + c * 3 + 1) * 131_713L % 9_000_000L)
        }
    }

    fun deposits(today: LocalDate): List<Deposit> = List(15) { i ->
        val brand = banks[i % banks.size]
        val start = today.minusDays(20L + i * 17L)
        Deposit(
            id = "bench-dep-$i", brand = brand, title = "Вклад ${i + 1}",
            principal = Money.ofMinor(15_000_000L + i * 4_250_000L), rateBp = 1000 + (i % 7) * 100,
            startDate = start, endDate = start.plusDays(180L + (i % 5) * 60L),
            method = if (i % 3 == 0) AccrualMethod.CAPITALIZATION else AccrualMethod.LINEAR,
            createdAt = start.toEpochDay() * 86_400_000L
        )
    }

    private val categories = listOf(
        "Супермаркеты" to "groceries", "Рестораны" to "restaurant", "Кафе" to "cafe", "АЗС" to "fuel",
        "Транспорт" to "transport", "Такси" to "taxi", "Аптеки" to "pharmacy", "Красота" to "beauty",
        "Одежда" to "clothing", "Онлайн-покупки" to "online", "Путешествия" to "travel", "На всё" to "base"
    )

    /** Six months back from [today]'s, each bank with 5–8 categories: [Pair.first] is the month key. */
    fun cashback(today: LocalDate): List<Pair<String, Map<BankBrand, List<CashbackItem>>>> {
        val current = CashbackMonth.keyOf(today)
        return (0 until 6).map { back ->
            val month = CashbackMonth.keyForOffset(current, -back)
            month to banks.mapIndexed { b, brand ->
                val count = 5 + (b + back) % 4
                brand to List(count) { n ->
                    val (name, group) = categories[(b * 3 + n * 2 + back) % categories.size]
                    CashbackItem("bench-cb-$month-${brand.key}-$n", month, brand, name, (1 + (b + n + back) % 5).toDouble(), groupKey = group)
                }.distinctBy { it.groupKey }
            }.toMap()
        }
    }

    private val subscriptionTitles = listOf(
        "Яндекс Плюс", "Кинопоиск", "Okko", "IVI", "Wink", "Spotify", "Звук", "Облако", "iCloud", "YouTube Premium",
        "Telegram Premium", "Литрес", "Строки", "Дом.ру", "Мегафон", "Фитнес", "Сбер Прайм", "Ozon Premium", "Delivery Club", "Тинькофф Pro"
    )

    fun subscriptions(today: LocalDate): List<Subscription> = subscriptionTitles.mapIndexed { i, title ->
        Subscription(
            id = "bench-sub-$i", title = title, priceMinor = 9_900L + i * 7_300L,
            period = when (i % 4) { 0 -> Period.MONTHLY; 1 -> Period(PeriodUnit.YEAR); 2 -> Period(PeriodUnit.WEEK); else -> Period.MONTHLY },
            nextDate = today.plusDays(1L + i * 2L), brand = banks[i % banks.size], remind = i % 5 == 0,
            createdAt = today.minusDays(40L + i).toEpochDay() * 86_400_000L
        )
    }
}
