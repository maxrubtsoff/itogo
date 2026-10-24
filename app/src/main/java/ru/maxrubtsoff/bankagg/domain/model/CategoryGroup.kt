package ru.maxrubtsoff.bankagg.domain.model

/**
 * A fixed cashback group shipped with the app. Banks word their categories in
 * many ways ("Медицинские услуги", "Медицина", "Здоровье и медицина"); every
 * cashback row points at one group by [key], so equal offers from different
 * banks line up for comparison and the home card. Users never edit groups.
 *
 * [key] doubles as the icon key resolved in ui/common/CategoryIcons.
 * [synonyms] are lowercase phrasings banks use; matching normalises both sides.
 */
data class CategoryGroup(
    val key: String,
    val name: String,
    val iconKey: String,
    val synonyms: List<String>
)

object CategoryGroups {

    /** The catch-all group for text that doesn't map to anything. */
    const val OTHER = "other"

    private fun g(key: String, name: String, vararg synonyms: String) =
        CategoryGroup(key, name, key, synonyms.toList())

    /** Display order. Keys must exist in the UI icon catalogue. */
    val all: List<CategoryGroup> = listOf(
        g("base", "Базовый кэшбэк", "базовый", "базовый кешбэк", "на все остальное", "остальные покупки"),
        g("groceries", "Супермаркеты", "супермаркет", "продукты", "продуктовые магазины", "гипермаркеты", "магазины продуктов", "продукты питания"),
        g("restaurant", "Рестораны", "ресторан", "рестораны и бары", "бары", "общепит"),
        g("cafe", "Кафе", "фастфуд", "фаст фуд", "кофейни", "кофе", "кафе и фастфуд"),
        g("fuel", "АЗС", "топливо", "заправки", "бензин", "автозаправки", "топливо и азс", "автозаправочные станции"),
        g("transport", "Транспорт", "общественный транспорт", "метро", "проезд", "поезда", "жд билеты"),
        g("taxi", "Такси", "такси и каршеринг", "каршеринг", "поездки на такси"),
        g("pharmacy", "Аптеки", "аптека", "лекарства", "товары для здоровья"),
        g("health", "Здоровье", "медицина", "медицинские услуги", "медуслуги", "клиники", "стоматология", "медицинские центры", "врачи"),
        g("beauty", "Красота", "салоны красоты", "косметика", "парфюмерия", "косметика и парфюмерия"),
        g("clothing", "Одежда", "одежда и обувь", "обувь", "аксессуары", "одежда и аксессуары"),
        g("electronics", "Электроника", "бытовая техника", "техника", "цифровая техника", "электроника и техника"),
        g("home", "Дом и ремонт", "дом", "ремонт", "строительные материалы", "товары для дома", "мебель", "строительные магазины"),
        g("online", "Онлайн-покупки", "маркетплейсы", "интернет магазины", "онлайн покупки", "покупки онлайн"),
        g("entertainment", "Развлечения", "кино", "кинотеатры", "театры", "концерты"),
        g("art", "Искусство и культура", "искусство", "культура", "музеи", "выставки", "галереи", "театры и музеи"),
        g("travel", "Путешествия", "отели", "авиабилеты", "туризм", "гостиницы", "бронирование отелей", "отели и гостиницы"),
        g("auto", "Авто", "автоуслуги", "автосервис", "автомойка", "автомойки", "шиномонтаж", "автозапчасти", "автотовары", "парковки", "техобслуживание"),
        g("mobile", "Связь и интернет", "связь", "мобильная связь", "интернет", "тв", "телевидение", "сотовая связь"),
        g("kids", "Дети", "детские товары", "товары для детей", "игрушки"),
        g("pets", "Животные", "зоотовары", "товары для животных", "ветеринария", "зоомагазины"),
        g("sport", "Спорт", "спорттовары", "фитнес", "фитнес клубы", "спортивные товары", "активный отдых", "спорт и отдых"),
        g("all", "Все покупки", "на все покупки", "любые покупки", "все категории"),
        g(OTHER, "Другое")
    )

    private val byKeyMap: Map<String, CategoryGroup> = all.associateBy { it.key }

    /**
     * Shops and services banks list by name: the name is kept as is, the group
     * only lends its icon ("Hoff" → Дом и ремонт). Keys are normalised.
     */
    private val brandHints: Map<String, String> = mapOf(
        "hoff" to "home", "леруа мерлен" to "home", "икеа" to "home",
        "сбертревел" to "travel", "островок" to "travel", "туту" to "travel",
        "яндекс лавка" to "groceries", "самокат" to "groceries", "пятерочка" to "groceries",
        "перекресток" to "groceries", "магнит" to "groceries", "лента" to "groceries", "вкусвилл" to "groceries",
        "lamoda" to "clothing",
        "wildberries" to "online", "ozon" to "online", "яндекс маркет" to "online",
        "м видео" to "electronics", "dns" to "electronics",
        "спортмастер" to "sport", "детский мир" to "kids",
        "золотое яблоко" to "beauty", "летуаль" to "beauty", "л этуаль" to "beauty",
        "яндекс еда" to "cafe", "кинопоиск" to "entertainment"
    )

    /**
     * A row stored as "Другое" gets the group the catalogue knows for its text
     * now (the catalogue grows; the stored row stays as it was). Others keep theirs.
     */
    fun regroup(category: String, groupKey: String): String {
        if (groupKey != OTHER) return groupKey
        val r = LexicalResolver.resolve(category)
        return if (r.kind == Resolution.Kind.EMPTY) OTHER else r.groupKey
    }

    /** The group whose icon a known shop/service name borrows, or null. */
    fun brandHint(text: String): String? = brandHints[CategoryMatcher.normalize(text)]

    /** The group for [key]; unknown keys fall back to [OTHER]. */
    fun byKey(key: String): CategoryGroup = byKeyMap[key] ?: byKeyMap.getValue(OTHER)

    /**
     * Groups whose name contains [query] (normalised); blank query → all.
     * Drives the "Все" list in the picker as the user types.
     */
    fun filter(query: String): List<CategoryGroup> {
        val q = CategoryMatcher.normalize(query)
        if (q.isEmpty()) return all
        return all.filter { CategoryMatcher.normalize(it.name).contains(q) }
    }
}
