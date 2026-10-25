package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The example data of the tours (U-54, F-32): what the real screens show while
 * a tour plays on them. Built from constants and [today] only — the same day
 * gives the same data, every time; nothing is random and nothing is read from
 * the phone. Every section has something in it, so no tour starts on an empty
 * screen. Amounts are [Money] in minor units.
 */
object TourSample {

    /** The example bank every tour uses; not a real one, so no logo of a real bank is borrowed. */
    val BANK = BankBrand("sample", "Банк-пример", 0xFF4F6D7A, letters = "Б")

    /** Everything one tour screen can ask for. */
    data class Data(
        val today: LocalDate,
        val banks: List<BankBrand>,
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val deposits: List<Deposit>,
        val fxOps: List<FxOperation>,
        val rates: List<FxRate>,
        val cash: CashBalance,
        val monthKey: String,
        val cashback: List<CashbackItem>,
        val subscriptions: List<Subscription>
    )

    fun data(today: LocalDate): Data = Data(
        today = today,
        banks = listOf(BANK, BankBrand.TBANK, BankBrand.ALFA, BankBrand.OZON),
        accounts = accounts(today),
        transactions = transactions(today),
        deposits = deposits(today),
        fxOps = fxOps(today),
        rates = rates(today),
        cash = cash(today),
        monthKey = CashbackMonth.keyOf(today),
        cashback = cashback(today),
        subscriptions = subscriptions(today)
    )

    /** Total capital over the example data, all four parts counted. */
    fun capital(today: LocalDate): CapitalSnapshot {
        val d = data(today)
        return CapitalCalculator.compute(d.accounts, d.deposits, d.fxOps, d.rates, d.cash, CapitalPart.entries.toSet(), today)
    }

    // --- Accounts -----------------------------------------------------------

    val ACCOUNT_SAMPLE = "${BANK.key}:${Currencies.RUB}"

    fun accounts(today: LocalDate): List<Account> {
        val at = noon(today)
        return listOf(
            Account(ACCOUNT_SAMPLE, BANK, BANK.title, Money.ofMinor(9_826_255), Currencies.RUB, at, checkedAt = at),
            Account("tbank:RUB", BankBrand.TBANK, BankBrand.TBANK.title, Money.ofMinor(6_105_795), Currencies.RUB, at, checkedAt = at),
            Account("ozon:RUB", BankBrand.OZON, BankBrand.OZON.title, Money.ofMinor(2_500_000), Currencies.RUB, at, checkedAt = at)
        )
    }

    /** The purchase of the «Баланс обновился» step: 1 073,40 ₽ in FAST-FUD, the bank says 97 189,15 left. */
    const val PURCHASE_MINOR = 107_340L
    const val PURCHASE_PAYEE = "FAST-FUD"
    const val BALANCE_AFTER_PURCHASE_MINOR = 9_718_915L

    fun purchase(today: LocalDate): Transaction = Transaction(
        id = "sample-tx-purchase", brand = BANK, accountId = ACCOUNT_SAMPLE, amount = Money.ofMinor(-PURCHASE_MINOR),
        currency = Currencies.RUB, kind = TransactionKind.EXPENSE, category = "Рестораны", counterparty = PURCHASE_PAYEE,
        postedAt = noon(today)
    )

    /** Payments seen in the pushes: one subscription already kept, one the app finds by itself. */
    fun transactions(today: LocalDate): List<Transaction> {
        fun pay(id: String, payee: String, minor: Long, date: LocalDate) = Transaction(
            id = id, brand = BankBrand.TBANK, accountId = "tbank:RUB", amount = Money.ofMinor(-minor), currency = Currencies.RUB,
            kind = TransactionKind.EXPENSE, category = "Подписки", counterparty = payee, postedAt = noon(date)
        )
        return buildList {
            for (back in 1..2) {
                add(pay("sample-tx-plus-$back", PLUS_PAYEE, 29_900, today.plusDays(PLUS_NEXT_OFFSET.toLong()).minusMonths(back.toLong())))
            }
            for (back in 0..2) {
                add(pay("sample-tx-okko-$back", FOUND_PAYEE, 39_900, today.minusDays(7).minusMonths(back.toLong())))
            }
        }
    }

    // --- Deposits -----------------------------------------------------------

    fun deposits(today: LocalDate): List<Deposit> = listOf(
        Deposit(
            "sample-dep-cushion", BANK, "Подушка", Money.ofMinor(100_000_000), 1320,
            today.minusDays(259), today.plusDays(106), createdAt = noon(today.minusDays(259))
        ),
        Deposit(
            "sample-dep-reserve", BANK, "Запас", Money.ofMinor(50_000_000), 1250,
            today.minusDays(110), today.plusDays(71), createdAt = noon(today.minusDays(110))
        )
    )

    /** What the example screenshot of a deposit "says": the tour adds it to the list. */
    fun scannedDepositDraft(today: LocalDate): DepositDraft = DepositDraft(
        principal = Money.ofMinor(50_000_000),
        rateBp = 1320,
        startDate = today.minusDays(324),
        endDate = today.plusDays(41),
        title = "На отпуск",
        method = AccrualMethod.LINEAR
    )

    /** The deposit the example screenshot becomes: the tour's list marks it. */
    const val HOLIDAY_ID = "sample-dep-holiday"

    fun scannedDeposit(today: LocalDate): Deposit {
        val d = scannedDepositDraft(today)
        return Deposit(
            HOLIDAY_ID, BANK, d.title, d.principal!!, d.rateBp!!, d.startDate!!, d.endDate!!,
            method = d.method ?: AccrualMethod.LINEAR, createdAt = noon(today)
        )
    }

    // --- Currency and cash --------------------------------------------------

    fun fxOps(today: LocalDate): List<FxOperation> = listOf(
        FxOperation("sample-fx-usd", "USD", FxKind.BUY, today.minusDays(120), 100_000, 9_120_000, noon(today.minusDays(120))),
        FxOperation("sample-fx-eur", "EUR", FxKind.BUY, today.minusDays(60), 30_000, 2_994_000, noon(today.minusDays(60)))
    )

    /** CBR rates of the example, rubles per unit ×1 000 000. */
    fun rates(today: LocalDate): List<FxRate> = listOf(
        FxRate("USD", today, 95_432_100),
        FxRate("EUR", today, 103_123_400)
    )

    fun cash(today: LocalDate): CashBalance = CashBalance(Money.ofMinor(1_500_000), noon(today))

    /** The sum the «Наличные» step changes it to. */
    const val CASH_AFTER_MINOR = 2_000_000L

    // --- Cashback -----------------------------------------------------------

    /** The month as it stands before the tour adds a bank: two banks, seven categories. */
    fun cashback(today: LocalDate): List<CashbackItem> {
        val month = CashbackMonth.keyOf(today)
        var n = 0
        fun item(brand: BankBrand, category: String, group: String, percent: Double) =
            CashbackItem("sample-cb-${n++}", month, brand, category, percent, groupKey = group)
        return listOf(
            item(BankBrand.ALFA, "Автоуслуги", "auto", 5.0),
            item(BankBrand.ALFA, "Транспорт", "transport", 5.0),
            item(BankBrand.ALFA, "На всё", "base", 1.0),
            item(BankBrand.TBANK, "Супермаркеты", "groceries", 5.0),
            item(BankBrand.TBANK, "Аптеки", "pharmacy", 5.0),
            item(BankBrand.TBANK, "Такси", "taxi", 3.0),
            item(BankBrand.TBANK, "Кафе", "cafe", 3.0)
        )
    }

    /**
     * The rows the example screenshot of cashback is read into: one percent
     * is left unread on purpose («Такое бывает») — the tour fills it in.
     */
    fun scannedCashbackLines(): List<ShotLine> = listOf(
        ShotLine("Супермаркеты", "groceries", 5.0, "Супермаркеты"),
        ShotLine("Рестораны", "restaurant", 5.0, "Рестораны"),
        ShotLine("Такси", "taxi", 3.0, "Такси"),
        ShotLine("Аптеки", "pharmacy", null, "Аптеки")
    )

    /** The percent the tour types into the unread row. */
    const val FILLED_PERCENT = 3.0

    // --- Subscriptions ------------------------------------------------------

    const val PLUS_PAYEE = "YANDEX PLUS"
    const val FOUND_PAYEE = "OKKO.TV"
    private const val PLUS_NEXT_OFFSET = 3

    fun subscriptions(today: LocalDate): List<Subscription> = listOf(
        Subscription(
            "sample-sub-plus", "Яндекс Плюс", 29_900, Period.MONTHLY, today.plusDays(PLUS_NEXT_OFFSET.toLong()),
            brand = BankBrand.TBANK, payee = PLUS_PAYEE, remind = true, createdAt = noon(today.minusDays(60))
        ),
        Subscription(
            "sample-sub-cloud", "Облако", 199_000, Period(PeriodUnit.YEAR), today.plusDays(120),
            brand = BankBrand.TBANK, createdAt = noon(today.minusDays(245))
        )
    )

    /** Noon UTC of [day] in epoch ms: one fixed instant per day, whatever the phone's time zone. */
    private fun noon(day: LocalDate): Long = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}
