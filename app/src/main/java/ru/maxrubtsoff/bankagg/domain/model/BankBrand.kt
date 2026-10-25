package ru.maxrubtsoff.bankagg.domain.model

/**
 * A bank (F-22). [key] is the stable id used across the app, in the database
 * and in backups. Three kinds:
 * - **known** — popular banks we know the Android apps of ([packages], declared
 *   in the manifest <queries> so installation and icons can be checked without
 *   QUERY_ALL_PACKAGES); keys like `sber`, unchanged since the first versions;
 * - **from the CBR directory** — `cbr:<registration number>`, name from the
 *   bundled list of the Bank of Russia;
 * - **the user's own** — `user:<uuid>`, any name, letters and colours or a picture.
 * Nothing is preinstalled: the user adds the banks they use ([BankDirectory]).
 * [defaultCurrency] is assumed for an amount with no currency mark (null —
 * never assume: a Belarusian bank's "руб." is not a Russian ruble).
 */
class BankBrand(
    val key: String,
    val title: String,
    val primaryColor: Long,
    val onPrimaryDark: Boolean = false,
    val isBusiness: Boolean = false,
    val packages: Set<String> = emptySet(),
    val defaultCurrency: String? = "RUB",
    /** Up to 3 letters for the logo when there's no app icon or picture. */
    val letters: String = lettersOf(title),
    /** Letter colour on [primaryColor]; null — black or white, whichever reads. */
    val textColor: Long? = null,
    /** The user's own picture for the logo (a file in the app's storage). */
    val logoFile: String? = null,
    /** Registration number in the CBR directory. */
    val regNumber: Int? = null,
    /** Extra words that name this bank in screenshots and notifications. */
    val aliases: List<String> = emptyList()
) {
    /** The canonical package used when storing a user's selection ("" when none). */
    val primaryPackage: String get() = packages.firstOrNull().orEmpty()

    val isKnown: Boolean get() = known.any { it.key == key }

    /** Display order: known banks as listed (largest first), the rest after them. */
    val order: Int get() = known.indexOf(this).let { if (it < 0) Int.MAX_VALUE else it }
    val isUser: Boolean get() = key.startsWith(USER_PREFIX)

    /** The logo as a shared look (F-33): what the editor edits and the avatar draws. */
    fun decor(): BrandDecor = BrandDecor(primaryColor, letters, textColor, logoFile)

    /** This bank under [title] with the look [d] (the editor's result). */
    fun withDecor(title: String, d: BrandDecor): BankBrand =
        copy(title = title, primaryColor = d.colorArgb, letters = d.letters, textColor = d.textColorArgb, logoFile = d.logoFile)

    fun copy(
        title: String = this.title,
        primaryColor: Long = this.primaryColor,
        packages: Set<String> = this.packages,
        defaultCurrency: String? = this.defaultCurrency,
        letters: String = this.letters,
        textColor: Long? = this.textColor,
        logoFile: String? = this.logoFile
    ) = BankBrand(key, title, primaryColor, onPrimaryDark, isBusiness, packages, defaultCurrency, letters, textColor, logoFile, regNumber, aliases)

    override fun equals(other: Any?): Boolean = other is BankBrand && other.key == key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = key

    companion object {
        const val CBR_PREFIX = "cbr:"
        const val USER_PREFIX = "user:"

        val SBER = BankBrand("sber", "Сбербанк", 0xFF21A038, packages = setOf("ru.sberbankmobile", "ru.sberbankmobile_alpha"),
            letters = "С", regNumber = 1481, aliases = listOf("сбер", "сбербанк"))
        val TBANK = BankBrand("tbank", "Т-Банк", 0xFFFFDD2D, onPrimaryDark = true, packages = setOf("com.idamob.tinkoff.android", "ru.tbank.mobile"),
            letters = "Т", regNumber = 2673, aliases = listOf("т банк", "тинькофф", "tinkoff", "т-банк", "tbank", "тбанк"))
        val ALFA = BankBrand("alfa", "Альфа-Банк", 0xFFEF3124, packages = setOf("ru.alfabank.mobile.android"),
            letters = "А", regNumber = 1326, aliases = listOf("альфа", "alfa"))
        val YANDEX = BankBrand("yandex", "Яндекс Пэй", 0xFF7B61FF, packages = setOf("com.yandex.bank"),
            letters = "Я", regNumber = 3027, aliases = listOf("яндекс пэй", "яндекс банк", "yandex pay", "яндекс", "yandex"))
        val MTS = BankBrand("mts", "МТС Деньги", 0xFFE30611, packages = setOf("ru.lewis.dbo"),
            letters = "М", regNumber = 2268, aliases = listOf("мтс", "mts"))
        // OCR reads the Latin "Ozon Premium" as "Огоп Ргет!ит".
        val OZON = BankBrand("ozon", "Ozon Банк", 0xFF005BFF, packages = setOf("ru.ozon.fintech.finance"),
            letters = "oz", regNumber = 3542, aliases = listOf("озон", "ozon", "огоп"))
        val ALFA_BUSINESS = BankBrand("alfa_business", "Альфа-Бизнес", 0xFFB3140A, isBusiness = true, packages = setOf("ru.alfabank.oavdo.amc"),
            letters = "АБ", aliases = listOf("альфа бизнес"))
        val TBANK_BUSINESS = BankBrand("tbank_business", "Т-Бизнес", 0xFF333333, isBusiness = true, packages = setOf("ru.tinkoff.sme"),
            letters = "ТБ", aliases = listOf("т бизнес", "тинькофф бизнес"))
        val BSB = BankBrand("bsb", "BSB Bank", 0xFF14387F, packages = setOf("by.bank.bsb.mobile"), defaultCurrency = null,
            letters = "БСБ", aliases = listOf("bsb", "бсб"))
        // Popular banks: known by name and colour; their apps' packages are filled
        // in as they're confirmed — until then the app links by notifications (F-23).
        val VTB = BankBrand("vtb", "ВТБ", 0xFF0A2896, packages = setOf("ru.vtb24.mobilebanking.android"), letters = "ВТБ", regNumber = 1000, aliases = listOf("втб", "vtb"))
        val GAZPROM = BankBrand("gpb", "Газпромбанк", 0xFF1E4C9A, packages = setOf("ru.gazprombank.android.mobilebank.app"), letters = "ГПБ", regNumber = 354, aliases = listOf("газпромбанк", "гпб"))
        val RSHB = BankBrand("rshb", "Россельхозбанк", 0xFF007A3D, letters = "РСХ", regNumber = 3349, aliases = listOf("россельхоз", "рсхб"))
        val MKB = BankBrand("mkb", "МКБ", 0xFFD52B1E, letters = "МКБ", regNumber = 1978, aliases = listOf("московский кредитный", "мкб"))
        val SOVCOM = BankBrand("sovcom", "Совкомбанк", 0xFFE4002B, letters = "СКБ", regNumber = 963, aliases = listOf("совкомбанк", "халва"))
        val DOMRF = BankBrand("domrf", "Банк ДОМ.РФ", 0xFF2D3A8C, letters = "ДОМ", regNumber = 2312, aliases = listOf("дом.рф", "дом рф"))
        val RAIF = BankBrand("raif", "Райффайзенбанк", 0xFFFFED00, onPrimaryDark = true, packages = setOf("ru.raiffeisennews"), letters = "R", regNumber = 3292, aliases = listOf("райффайзен", "raiffeisen"))
        val PSB = BankBrand("psb", "ПСБ", 0xFFF26522, packages = setOf("logo.com.mbanking"), letters = "ПСБ", regNumber = 3251, aliases = listOf("псб", "промсвязьбанк"))
        val URALSIB = BankBrand("uralsib", "Уралсиб", 0xFF1F3A93, letters = "УС", regNumber = 2275, aliases = listOf("уралсиб"))

        /** A bank that isn't known any more (deleted, or a backup from elsewhere). */
        val UNKNOWN = BankBrand("unknown", "Другой банк", 0xFF6750A4, letters = "?")

        /** Banks we know the apps of; also the "popular" hints, largest first. */
        val known: List<BankBrand> = listOf(
            SBER, VTB, GAZPROM, ALFA, RSHB, MKB, SOVCOM, TBANK, DOMRF, RAIF, PSB, URALSIB, OZON, YANDEX, MTS,
            ALFA_BUSINESS, TBANK_BUSINESS, BSB
        )

        /** Kept for older code paths: the known banks. */
        val catalogue: List<BankBrand> get() = known

        /** Every bank resolvable now: known plus the user's directory. */
        val all: List<BankBrand> get() = known + BankDirectory.extra()

        fun fromPackage(pkg: String): BankBrand =
            all.firstOrNull { pkg in it.packages } ?: UNKNOWN

        // What the user changed about a bank comes first: a picture or letters
        // put on a known bank must not be overwritten by its built-in look (B-49).
        fun fromKey(key: String): BankBrand =
            BankDirectory.find(key) ?: known.firstOrNull { it.key == key } ?: if (key == UNKNOWN.key) UNKNOWN
            else UNKNOWN.let { BankBrand(key, "Банк", it.primaryColor, letters = "?") }

        /** The known bank with this CBR registration number, if any. */
        fun fromRegNumber(rn: Int): BankBrand? = known.firstOrNull { it.regNumber == rn }

        /** Number of distinct banks selected (a bank may have several packages). */
        fun selectedBankCount(selectedPackages: Set<String>): Int =
            all.count { brand -> brand.packages.any { it in selectedPackages } }

        /** Every package of a known bank, for the manifest <queries> mirror. */
        val allPackages: Set<String> get() = all.flatMap { it.packages }.toSet()

        /** "Россельхозбанк" → "Р", "Банк ДОМ.РФ" → "ДР", "Ozon" → "O": up to 3 initials. */
        fun lettersOf(title: String): String {
            val words = title.split(' ', '-', '.', '"', '«', '»').filter { it.isNotBlank() && it.lowercase() != "банк" }
            val initials = words.take(3).joinToString("") { it.first().uppercase() }
            return initials.ifEmpty { title.take(1).uppercase() }
        }
    }
}

/**
 * The banks the app knows beyond [BankBrand.known] — CBR banks and the user's
 * own — so [BankBrand.fromKey] resolves them anywhere. The data layer keeps it
 * filled from storage; tests may [replace] it.
 */
object BankDirectory {
    @Volatile private var banks: Map<String, BankBrand> = emptyMap()

    fun replace(list: List<BankBrand>) {
        banks = list.associateBy { it.key }
    }

    fun find(key: String): BankBrand? = banks[key]

    fun extra(): List<BankBrand> = banks.values.toList()
}
