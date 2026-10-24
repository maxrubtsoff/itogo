package ru.maxrubtsoff.bankagg.domain.model

/** One currency the app knows: ISO code, Russian name, and a symbol when one is widely known. */
data class CurrencyInfo(val code: String, val name: String, val symbol: String?)

/**
 * The fixed catalogue of currencies the Bank of Russia quotes (XML_daily.asp),
 * plus the ruble itself. Shipped in code — no network needed for the list.
 * Drives symbols in [Money.format], the currency picker and the notification
 * parser's currency detection.
 */
object Currencies {

    const val RUB = "RUB"

    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("RUB", "Российский рубль", "₽"),
        CurrencyInfo("USD", "Доллар США", "$"),
        CurrencyInfo("EUR", "Евро", "€"),
        CurrencyInfo("CNY", "Китайский юань", "¥"),
        CurrencyInfo("AUD", "Австралийский доллар", null),
        CurrencyInfo("AZN", "Азербайджанский манат", "₼"),
        CurrencyInfo("DZD", "Алжирский динар", null),
        CurrencyInfo("GBP", "Фунт стерлингов", "£"),
        CurrencyInfo("AMD", "Армянский драм", "֏"),
        CurrencyInfo("BHD", "Бахрейнский динар", null),
        CurrencyInfo("BYN", "Белорусский рубль", "Br"),
        CurrencyInfo("BOB", "Боливиано", null),
        CurrencyInfo("BRL", "Бразильский реал", "R$"),
        CurrencyInfo("HUF", "Венгерский форинт", null),
        CurrencyInfo("VND", "Вьетнамский донг", "₫"),
        CurrencyInfo("HKD", "Гонконгский доллар", null),
        CurrencyInfo("GEL", "Грузинский лари", "₾"),
        CurrencyInfo("DKK", "Датская крона", null),
        CurrencyInfo("AED", "Дирхам ОАЭ", null),
        CurrencyInfo("EGP", "Египетский фунт", null),
        CurrencyInfo("INR", "Индийская рупия", "₹"),
        CurrencyInfo("IDR", "Индонезийская рупия", null),
        CurrencyInfo("IRR", "Иранский риал", null),
        CurrencyInfo("KZT", "Казахстанский тенге", "₸"),
        CurrencyInfo("CAD", "Канадский доллар", null),
        CurrencyInfo("QAR", "Катарский риал", null),
        CurrencyInfo("KGS", "Киргизский сом", null),
        CurrencyInfo("CUP", "Кубинское песо", null),
        CurrencyInfo("MDL", "Молдавский лей", null),
        CurrencyInfo("MNT", "Монгольский тугрик", "₮"),
        CurrencyInfo("MMK", "Мьянманский кьят", null),
        CurrencyInfo("NGN", "Нигерийская найра", "₦"),
        CurrencyInfo("NZD", "Новозеландский доллар", null),
        CurrencyInfo("NOK", "Норвежская крона", null),
        CurrencyInfo("OMR", "Оманский риал", null),
        CurrencyInfo("PLN", "Польский злотый", "zł"),
        CurrencyInfo("SAR", "Саудовский риял", null),
        CurrencyInfo("RON", "Румынский лей", null),
        CurrencyInfo("XDR", "СДР", null),
        CurrencyInfo("SGD", "Сингапурский доллар", null),
        CurrencyInfo("TJS", "Таджикский сомони", null),
        CurrencyInfo("THB", "Тайский бат", "฿"),
        CurrencyInfo("BDT", "Бангладешская така", "৳"),
        CurrencyInfo("TRY", "Турецкая лира", "₺"),
        CurrencyInfo("TMT", "Туркменский манат", null),
        CurrencyInfo("UZS", "Узбекский сум", null),
        CurrencyInfo("UAH", "Украинская гривна", "₴"),
        CurrencyInfo("CZK", "Чешская крона", null),
        CurrencyInfo("SEK", "Шведская крона", null),
        CurrencyInfo("CHF", "Швейцарский франк", null),
        CurrencyInfo("ETB", "Эфиопский быр", null),
        CurrencyInfo("RSD", "Сербский динар", null),
        CurrencyInfo("ZAR", "Южноафриканский рэнд", null),
        CurrencyInfo("KRW", "Южнокорейская вона", "₩"),
        CurrencyInfo("JPY", "Японская иена", null)
    )

    private val byCode: Map<String, CurrencyInfo> = all.associateBy { it.code }

    fun of(code: String): CurrencyInfo? = byCode[code.uppercase()]

    /** The symbol for [code], or the code itself when no symbol is common ("1 000 KZT"). */
    fun symbolOf(code: String): String = byCode[code.uppercase()]?.symbol ?: code.uppercase()

    fun nameOf(code: String): String = byCode[code.uppercase()]?.name ?: code.uppercase()

    /** Foreign currencies only (no ruble), for the picker. */
    val foreign: List<CurrencyInfo> = all.filter { it.code != RUB }

    /**
     * Picker order: currencies already in use, then USD, EUR, CNY, then the rest
     * alphabetically by Russian name. A non-blank [query] filters by code or name.
     */
    fun pickerOrder(used: List<String>, query: String = ""): List<CurrencyInfo> {
        val q = query.trim().lowercase()
        val usedInfo = used.mapNotNull { of(it) }.filter { it.code != RUB }.distinct()
        val top = listOf("USD", "EUR", "CNY").mapNotNull { of(it) }
        val rest = foreign.sortedBy { it.name }
        val ordered = (usedInfo + top + rest).distinct()
        if (q.isEmpty()) return ordered
        return ordered.filter { it.code.lowercase().contains(q) || it.name.lowercase().contains(q) }
    }
}
