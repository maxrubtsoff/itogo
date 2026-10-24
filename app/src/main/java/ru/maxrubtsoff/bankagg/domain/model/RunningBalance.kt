package ru.maxrubtsoff.bankagg.domain.model

/**
 * An account's balance as the app knows it (B-29). [checkedAt] — when it was
 * last given exactly, by the bank's own «Доступно …» or by the user; null —
 * never, the app doesn't know it yet. [estimated] — operations without a
 * balance were added on top since then, so the sum is the app's own count.
 */
data class BalanceState(val balanceMinor: Long, val updatedAt: Long, val checkedAt: Long?, val estimated: Boolean)

/**
 * Banks like Alfa don't write the balance in their pushes. For them the app
 * keeps counting: the last exact balance plus every operation after it. A
 * balance from the bank, or one the user typed, is the new starting point.
 */
object RunningBalance {

    /** A new operation of [amountMinor] (negative — spent), with the bank's [bankBalanceMinor] when the push has one. */
    fun apply(state: BalanceState?, amountMinor: Long, bankBalanceMinor: Long?, postedAt: Long): BalanceState {
        if (state == null) {
            return if (bankBalanceMinor != null) BalanceState(bankBalanceMinor, postedAt, postedAt, false)
            else BalanceState(0L, postedAt, null, false)
        }
        val updatedAt = maxOf(state.updatedAt, postedAt)
        val checkedAt = state.checkedAt
        return when {
            // The bank's figure wins unless a newer exact one is already there.
            bankBalanceMinor != null && (checkedAt == null || postedAt >= checkedAt) ->
                BalanceState(bankBalanceMinor, updatedAt, postedAt, false)
            // Nothing to count from yet: the user is asked for the balance.
            checkedAt == null -> state.copy(updatedAt = updatedAt)
            // Already inside the exact balance it is older than.
            postedAt < checkedAt -> state.copy(updatedAt = updatedAt)
            else -> BalanceState(state.balanceMinor + amountMinor, updatedAt, checkedAt, true)
        }
    }

    /** The user typed what the bank's app shows. */
    fun setByUser(state: BalanceState?, balanceMinor: Long, at: Long): BalanceState =
        BalanceState(balanceMinor, maxOf(state?.updatedAt ?: at, at), at, false)
}
