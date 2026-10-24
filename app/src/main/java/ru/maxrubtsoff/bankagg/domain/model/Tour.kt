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
    ACCOUNT_ROW, CURRENCY_ROW, CASH_SUM, FINDING
}

data class TourStep(
    val target: TourTarget,
    val title: String,
    val text: String,
    val action: TourAction = TourAction.NEXT
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
            add(TourStep(TourTarget.BOTTOM_BAR, "Четыре вкладки", "Обзор — всё вместе, Капитал — по частям, Платежи — кэшбэк и подписки, Настройки — остальное."))
            add(TourStep(TourTarget.HIDE_EYE, "Суммы можно скрыть", "Глаз наверху Обзора прячет все суммы. То же — двумя пальцами вниз по экрану."))
        }
        TourSection.DEPOSITS -> listOf(
            TourStep(TourTarget.ADD_BUTTON, "Добавить вклад", "Плюс открывает два пути: скрин из банка или ввод вручную."),
            TourStep(TourTarget.ADD_SHEET, "Со скрина быстрее", "Приложение прочитает банк, сумму, ставку и даты. Останется сверить и сохранить."),
            TourStep(TourTarget.LIST_ROW, "Вклад в списке", "Сколько на нём сейчас и сколько уже набежало. Касание открывает карточку: доход по месяцам, пополнения.")
        )
        TourSection.CASHBACK -> listOf(
            TourStep(TourTarget.ADD_BUTTON, "Добавить кэшбэк", "Скрин категорий из приложения банка — и они разложатся сами."),
            TourStep(TourTarget.MISSING_PERCENT, "Такое бывает", "Если процент не прочитался, его можно вписать здесь вручную.", TourAction.FILL_AND_NEXT),
            TourStep(TourTarget.BANK_PANEL, "Кэшбэк месяца", "Категории каждого банка — плашками. Карандаш меняет кэшбэк банка.")
        )
        TourSection.ACCOUNTS -> listOf(
            TourStep(TourTarget.ACCOUNT_ROW, "Счета обновляются сами", "Остаток берётся из уведомлений банка. Касание — сверить с банком, если разошлось."),
            TourStep(TourTarget.ACCOUNT_ROW, "Не всё в капитал", "Долгое нажатие на счёт — не учитывать его в общем капитале.")
        )
        TourSection.CURRENCY -> listOf(
            TourStep(TourTarget.ADD_BUTTON, "Купил валюту", "Сумма и курс покупки. Дальше приложение само считает по курсу ЦБ."),
            TourStep(TourTarget.CURRENCY_ROW, "Сколько это в рублях", "Стоимость сейчас и разница с ценой покупки. Касание — все покупки и продажи.")
        )
        TourSection.CASH -> listOf(
            TourStep(TourTarget.CASH_SUM, "Наличные — одной суммой", "Касание меняет сумму. Без подробностей: сколько на руках.")
        )
        TourSection.SUBSCRIPTIONS -> buildList {
            add(TourStep(TourTarget.FINDING, "Подписки находятся сами", "Одинаковые списания раз в месяц или год — приложение спросит, подписка ли это."))
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

    /** The tour after the first setup: once, unless it was taken or skipped. */
    fun startAfterSetup(done: Set<TourSection>): Boolean = TourSection.GENERAL !in done
}
