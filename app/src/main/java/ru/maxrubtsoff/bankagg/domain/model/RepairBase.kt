package ru.maxrubtsoff.bankagg.domain.model

/** One log entry of an account as the current parser reads it: when, and the exact balance it gave (B-90). */
data class RepairEvent(val postedAt: Long, val balanceMinor: Long?)

/**
 * What a repaired account counts from. The exact balance the bank gave in a
 * push that turned out not to be an operation (an advertisement) is no base.
 */
sealed interface RepairBase {
    /** The figure is sound: the current balance minus operations after it. */
    data class Keep(val checkedAt: Long, val balanceMinor: Long) : RepairBase

    /** The figure came from a false push: take the latest exact balance the log still has. */
    data class FromJournal(val checkedAt: Long, val balanceMinor: Long) : RepairBase

    /** Nothing exact to count from (never checked, or the only exact figure was false). */
    data object None : RepairBase

    companion object {
        /**
         * [changedPostedAts] — times of the log entries the new parser reads differently.
         * A manual balance has no such entry, so it is never touched.
         */
        fun pick(account: Account, accountEvents: List<RepairEvent>, changedPostedAts: Set<Long>, sumSince: Long): RepairBase {
            val checkedAt = account.checkedAt ?: return None
            if (checkedAt !in changedPostedAts) {
                return Keep(checkedAt, account.balance.minor - sumSince)
            }
            val last = accountEvents.filter { it.balanceMinor != null }.maxByOrNull { it.postedAt }
                ?: return None
            return FromJournal(last.postedAt, last.balanceMinor!!)
        }
    }
}
