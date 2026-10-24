package ru.maxrubtsoff.bankagg.domain.model

/**
 * The tour (F-30): short walks through the app on example data. Nothing a
 * tour shows is saved; the only real things in it are the system's ones —
 * access to notifications. Which steps show depends on what the person keeps.
 */
enum class TourSection(val key: String, val feature: Feature?) {
    GENERAL("general", null),
    ACCOUNTS("accounts", Feature.ACCOUNTS),
    DEPOSITS("deposits", Feature.DEPOSITS),
    CURRENCY("currency", Feature.CURRENCY),
    CASH("cash", Feature.CASH),
    CASHBACK("cashback", Feature.CASHBACK),
    SUBSCRIPTIONS("subscriptions", Feature.SUBSCRIPTIONS);

    companion object {
        fun fromKey(key: String?): TourSection? = entries.firstOrNull { it.key == key }
    }
}

/** What a step's buttons do. */
enum class TourAction {
    /** «Далее». */
    NEXT,
    /** «Позже» or «Разрешить» — the real system setting; the tour goes on either way. */
    ALLOW_ACCESS,
    /** «Далее» also fills the example field the step points at (no games, F-30). */
    FILL_AND_NEXT
}

/** What a step points at on its example screen. */
enum class TourTarget {
    ACCESS_TILE, BOTTOM_BAR, HIDE_EYE,
    ADD_BUTTON, ADD_SHEET, LIST_ROW,
    MISSING_PERCENT, BANK_PANEL,
    ACCOUNT_ROW, CURRENCY_ROW, CASH_SUM, FINDING,
    /** The thumbnail of the example screenshot while it is being read (F-34). */
    SHOT_THUMB
}

/**
 * What the example "plays" on the real screen during a step (F-32). The
 * finger and the screen's own changes are drawn by the UI; here is only which
 * action it is, so the order of a tour is data under test.
 */
enum class TourAnim {
    /** Nothing moves: the comment alone. */
    NONE,
    /** The finger taps the step's target — the plus, the eye. */
    TAP_ADD,
    /** The sheet with the ways to add rises. */
    OPEN_SHEET,
    /** «Со скринов»: an example screenshot is picked. */
    PICK_SHOTS,
    /** The scan line runs over the screenshot ([ScanReveal] in the UI). */
    SCAN,
    /** The recognised fields appear one after another. */
    REVEAL_FIELDS,
    /** A category is filled in or marked as the best one. */
    MARK_CATEGORY,
    /** An amount changes after a bank's notification, a purchase, a new sum. */
    BALANCE_CHANGE
}

/**
 * How a step lights its target (B-48): [FRAMED] — a window in the veil with a
 * brass frame round it; [DIM_ONLY] — only the veil, no frame, for targets that
 * fill the width (a sheet, the bottom bar) and whose frame would be cut by the
 * screen's edges.
 */
enum class TourHighlight {
    FRAMED, DIM_ONLY;

    companion object {
        /** Full-width targets are only dimmed around; the rest get a frame. */
        fun of(target: TourTarget): TourHighlight = when (target) {
            TourTarget.ADD_SHEET, TourTarget.BOTTOM_BAR -> DIM_ONLY
            else -> FRAMED
        }
    }
}

/**
 * [anim] is what plays on the screen; the next action never starts by itself
 * while the comment is unread ([waitForRead], F-32): «Далее» goes on.
 */
data class TourStep(
    val target: TourTarget,
    val title: String,
    val text: String,
    val action: TourAction = TourAction.NEXT,
    val anim: TourAnim = TourAnim.NONE,
    val waitForRead: Boolean = true,
    val highlight: TourHighlight = TourHighlight.of(target)
)

/**
 * «Что такое Itogo» — the intro slides (F-29) are shown once, at the very first
 * launch, and never again (B-50): neither on later launches nor from anywhere
 * in the app. The mark travels in the backup, so a restore or a re-install with
 * a copy doesn't bring them back.
 */
object IntroSlidesRule {
    fun show(onboarded: Boolean, shown: Boolean): Boolean = !onboarded && !shown
}

object TourScript {

    /** The sections whose tours the person can take: the kept ones, after the general one. */
    fun sections(features: Features): List<TourSection> =
        listOf(TourSection.GENERAL) + TourSection.entries.filter { s -> s.feature != null && features.has(s.feature) }

    fun steps(section: TourSection, features: Features, notificationAccess: Boolean): List<TourStep> = when (section) {
        TourSection.GENERAL -> buildList {
            if (features.collectsNotifications && !notificationAccess) add(
                TourStep(
                    TourTarget.ACCESS_TILE,
                    "Счета соберутся сами",
                    "Приложение читает уведомления выбранных банков и находит в них операции и остатки. Ничего не уходит с телефона.",
                    TourAction.ALLOW_ACCESS
                )
            )
            // Only the tabs that are really there, and the eye lives on «Обзор» (U-50).
            add(TourStep(TourTarget.BOTTOM_BAR, tabsTitle(features), tabsText(features)))
            if (features.showOverview) add(
                TourStep(TourTarget.HIDE_EYE, "Суммы можно скрыть", "Глаз наверху Обзора прячет все суммы. То же — двумя пальцами вниз по экрану.", anim = TourAnim.TAP_ADD)
            ) else add(
                TourStep(TourTarget.HIDE_EYE, "Суммы можно скрыть", "Глаз в шапке прячет все суммы. То же — двумя пальцами вниз по экрану.", anim = TourAnim.TAP_ADD)
            )
        }
        // TourD1–D3: «+» → шторка → скрин читается → вклад встаёт в список.
        TourSection.DEPOSITS -> listOf(
            TourStep(TourTarget.ADD_BUTTON, "Добавить вклад", "Плюс открывает два пути: скрин из банка или ввод вручную.", anim = TourAnim.TAP_ADD),
            TourStep(TourTarget.ADD_SHEET, "Со скрина быстрее", "Приложение прочитает банк, сумму, ставку и даты. Останется сверить и сохранить.", anim = TourAnim.OPEN_SHEET),
            TourStep(TourTarget.SHOT_THUMB, "Читает сам", "Банк, сумма, ставка и даты — со скрина.", anim = TourAnim.SCAN),
            TourStep(TourTarget.LIST_ROW, "Вклад в списке", "Сколько на нём сейчас и сколько уже набежало. Касание открывает карточку: доход по месяцам, пополнения.", anim = TourAnim.REVEAL_FIELDS)
        )
        // TourC1–C5: «+» → шторка → скрин → скан → поля → процент вручную → плашка банка.
        TourSection.CASHBACK -> listOf(
            TourStep(TourTarget.ADD_BUTTON, "Добавить кэшбэк", "Скрин категорий из приложения банка — и они разложатся сами.", anim = TourAnim.TAP_ADD),
            TourStep(TourTarget.ADD_SHEET, "Со скрина быстрее", "Вручную — если скрина под рукой нет.", anim = TourAnim.OPEN_SHEET),
            TourStep(TourTarget.ADD_SHEET, "Выбираем скрин", "Можно сразу несколько — по одному на банк.", anim = TourAnim.PICK_SHOTS),
            TourStep(TourTarget.SHOT_THUMB, "Читает сам", "Банк, категории и проценты определяются сами.", anim = TourAnim.SCAN),
            TourStep(TourTarget.MISSING_PERCENT, "Такое бывает", "Если процент не прочитался, его можно вписать здесь вручную.", TourAction.FILL_AND_NEXT, anim = TourAnim.REVEAL_FIELDS),
            TourStep(TourTarget.BANK_PANEL, "Кэшбэк месяца", "Категории каждого банка — плашками. Карандаш меняет кэшбэк банка. Лучший процент по категории подсвечен.", anim = TourAnim.MARK_CATEGORY)
        )
        // TourA1–A2: счета приходят из уведомлений; пришло — остаток сменился.
        TourSection.ACCOUNTS -> listOf(
            TourStep(TourTarget.ACCOUNT_ROW, "Счета из уведомлений", "Банк присылает уведомление — приложение его читает."),
            TourStep(TourTarget.ACCOUNT_ROW, "Баланс обновился", "Остаток берётся из уведомления, покупка — в истории счёта. Касание — сверить с банком, если разошлось.", anim = TourAnim.BALANCE_CHANGE),
            TourStep(TourTarget.ACCOUNT_ROW, "Не всё в капитал", "Долгое нажатие на счёт — не учитывать его в общем капитале.")
        )
        TourSection.CURRENCY -> listOf(
            TourStep(TourTarget.ADD_BUTTON, "Купил валюту", "Сумма и курс покупки. Дальше приложение само считает по курсу ЦБ.", anim = TourAnim.TAP_ADD),
            TourStep(TourTarget.CURRENCY_ROW, "Сколько это в рублях", "Стоимость сейчас и разница с ценой покупки. Касание — все покупки и продажи.")
        )
        TourSection.CASH -> listOf(
            TourStep(TourTarget.CASH_SUM, "Наличные — одной суммой", "Касание меняет сумму. Без подробностей: сколько на руках.", anim = TourAnim.BALANCE_CHANGE)
        )
        TourSection.SUBSCRIPTIONS -> buildList {
            add(TourStep(TourTarget.FINDING, "Подписки находятся сами", "Одинаковые списания раз в месяц или год — приложение спросит, подписка ли это.", anim = TourAnim.REVEAL_FIELDS))
            add(TourStep(TourTarget.LIST_ROW, "Ближайшие списания", "Сколько и когда спишут. Напоминание — за день до списания."))
            if (!notificationAccess) add(
                TourStep(TourTarget.ACCESS_TILE, "Нужны уведомления", "Без доступа к уведомлениям списания не видны и подписки не находятся.", TourAction.ALLOW_ACCESS)
            )
        }
    }

    /**
     * «Как это работает» in an empty section: while there's nothing in it yet and
     * its tour hasn't been taken, and only for a kept section.
     */
    fun offerInSection(section: TourSection, features: Features, hasData: Boolean, done: Set<TourSection>): Boolean =
        (section.feature == null || features.has(section.feature)) && !hasData && section !in done

    /** «Четыре вкладки» only when there are four (U-50). */
    internal fun tabsTitle(features: Features): String = when (features.tabs.size) {
        2 -> "Две вкладки"
        3 -> "Три вкладки"
        else -> "Четыре вкладки"
    }

    internal fun tabsText(features: Features): String = buildList {
        if (features.showOverview) add("Обзор — всё вместе")
        if (features.showCapital) add("Капитал — по частям")
        if (features.showPayments) add("Платежи — кэшбэк и подписки")
        add("Настройки — остальное")
    }.joinToString(", ") + "."

    /** The tour after the first setup: once, unless it was taken or skipped. */
    fun startAfterSetup(done: Set<TourSection>): Boolean = TourSection.GENERAL !in done
}
