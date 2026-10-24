package ru.maxrubtsoff.bankagg.domain.model

/**
 * What an empty Overview (and empty Accounts) offers (B-23): it depends on what
 * the user has already done and on the sections they keep (F-21), so it never
 * asks to pick banks that are picked or to add a section that is switched off.
 */
object EmptyStart {

    enum class Lead {
        /** Accounts kept, no bank collects notifications yet. */
        PICK_BANKS,
        /** Banks are chosen, but the app may not read notifications. */
        GRANT_ACCESS,
        /** Banks are chosen and access is on: the first operation brings the balance. */
        WAITING,
        /** Accounts aren't kept: only the manual sections remain. */
        NONE
    }

    data class Plan(
        val lead: Lead,
        val addDeposit: Boolean,
        val addCurrency: Boolean,
        val setCash: Boolean
    )

    fun plan(features: Features, collectingBanks: Int, notificationAccess: Boolean): Plan {
        val lead = when {
            !features.has(Feature.ACCOUNTS) -> Lead.NONE
            collectingBanks == 0 -> Lead.PICK_BANKS
            !notificationAccess -> Lead.GRANT_ACCESS
            else -> Lead.WAITING
        }
        return Plan(
            lead = lead,
            addDeposit = features.has(Feature.DEPOSITS),
            addCurrency = features.has(Feature.CURRENCY),
            setCash = features.has(Feature.CASH)
        )
    }
}
