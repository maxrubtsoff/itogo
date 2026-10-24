package ru.maxrubtsoff.bankagg.domain.model

/** What the user keeps in the app (F-21); a section that's off is gone everywhere. */
enum class Feature(val key: String, val capitalPart: CapitalPart?) {
    ACCOUNTS("accounts", CapitalPart.ACCOUNTS),
    DEPOSITS("deposits", CapitalPart.DEPOSITS),
    CURRENCY("currency", CapitalPart.CURRENCY),
    CASH("cash", CapitalPart.CASH),
    CASHBACK("cashback", null),
    SUBSCRIPTIONS("subscriptions", null);

    companion object {
        fun fromKey(key: String?): Feature? = entries.firstOrNull { it.key == key }
    }
}

/** The tabs of the bottom bar, in the order they stand there (U-50). */
enum class AppTab { OVERVIEW, CAPITAL, PAYMENTS, SETTINGS }

/**
 * The switched-on sections and what follows from them. Not the same as
 * "counts in the total capital" (F-12): there a part is shown but not summed;
 * here a section isn't shown at all. Data of a switched-off section stays.
 */
data class Features(val enabled: Set<Feature>) {

    fun has(f: Feature): Boolean = f in enabled

    /** Capital parts that exist in the app. */
    val capitalParts: Set<CapitalPart> = enabled.mapNotNull { it.capitalPart }.toSet()

    val showCapital: Boolean get() = capitalParts.isNotEmpty()
    val showCashback: Boolean get() = has(Feature.CASHBACK)
    val showSubscriptions: Boolean get() = has(Feature.SUBSCRIPTIONS)

    /** The «Платежи» tab: cashback, subscriptions, or both. */
    val showPayments: Boolean get() = showCashback || showSubscriptions

    /**
     * The «Обзор» tab shows the parts of the capital and the cashback card:
     * without both there is nothing on it, so it is hidden (U-50).
     */
    val showOverview: Boolean get() = showCapital || showCashback

    /** The tabs of the bottom bar, in order; «Настройки» is always there. */
    val tabs: List<AppTab>
        get() = buildList {
            if (showOverview) add(AppTab.OVERVIEW)
            if (showCapital) add(AppTab.CAPITAL)
            if (showPayments) add(AppTab.PAYMENTS)
            add(AppTab.SETTINGS)
        }

    /** Where the app opens, and where anything pointing at a hidden tab goes. */
    val firstTab: AppTab get() = tabs.first()

    /** The capture log is about operations on accounts (U-51). */
    val showCaptureLog: Boolean get() = has(Feature.ACCOUNTS)

    /**
     * Accounts and subscriptions come from bank notifications (subscriptions are
     * found by repeated charges): the listener, access request and log live with them.
     */
    val collectsNotifications: Boolean get() = has(Feature.ACCOUNTS) || has(Feature.SUBSCRIPTIONS)

    /** Banks are needed by anything tied to a bank: accounts, deposits, cashback. */
    val needsBanks: Boolean get() = has(Feature.ACCOUNTS) || has(Feature.DEPOSITS) || has(Feature.CASHBACK)

    /** «Новые банки» on the «Обзор»: only where the user curates banks, and the tab exists (U-52). */
    val showNewBanksPrompt: Boolean get() = collectsNotifications && needsBanks

    /** The notification-access banner sits on «Обзор» while accounts keep that tab (U-52). */
    val showNotifAccessOnOverview: Boolean get() = collectsNotifications && has(Feature.ACCOUNTS)

    /** Without accounts «Обзор» may be hidden: the banner moves to «Платежи» (U-52). */
    val showNotifAccessOnPayments: Boolean get() = collectsNotifications && !has(Feature.ACCOUNTS)

    /** A tap switches a section; the last one can't be switched off. */
    fun toggle(f: Feature): Features = when {
        f !in enabled -> Features(enabled + f)
        enabled.size == 1 -> this
        else -> Features(enabled - f)
    }

    companion object {
        val ALL = Features(Feature.entries.toSet())

        /** Marks subscriptions switched off: a choice saved before they existed keeps them on (F-30). */
        private const val NO_SUBSCRIPTIONS = "no:subscriptions"

        fun decode(keys: Set<String>?): Features {
            val picked = keys?.mapNotNull { Feature.fromKey(it) }?.toSet()?.takeIf { it.isNotEmpty() } ?: return ALL
            return Features(if (NO_SUBSCRIPTIONS in keys) picked - Feature.SUBSCRIPTIONS else picked + Feature.SUBSCRIPTIONS)
        }

        fun encode(f: Features): Set<String> =
            f.enabled.map { it.key }.toSet() + if (Feature.SUBSCRIPTIONS in f.enabled) emptySet() else setOf(NO_SUBSCRIPTIONS)
    }
}
