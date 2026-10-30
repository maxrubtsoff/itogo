package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.RepairBase
import ru.maxrubtsoff.bankagg.domain.model.RepairEvent
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.parser.ParsedOp
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner

/**
 * Runs the current parser over the log and fixes what the old one got wrong
 * (B-45: outgoing transfers recorded as income; B-46: advertisements recorded
 * as operations). The log keeps the raw text of every push, so nothing has to
 * be edited by hand.
 *
 * An operation whose text now reads the same is left alone. A changed one is
 * replaced, a text that is no longer an operation loses its operation, and the
 * account's counted balance is re-counted from its last exact figure, so the
 * balance and the reconciliation become right by themselves.
 */
class RepairFromJournal(
    private val events: EventRepository,
    private val ledger: LedgerRepository,
    private val tx: TransactionRunner
) {

    /** Returns how many log entries were fixed. */
    suspend operator fun invoke(): Int = tx.run {
        val changes = events.all().mapNotNull { event ->
            val op = NotificationParser.parse(event.title, event.text, event.brand.defaultCurrency)
            val content = RecordNotification.contentHash(event.packageName, event.text, op)
            if (content == event.contentHash) null else Change(event.id, event.dedupHash, content, op, event)
        }
        if (changes.isEmpty()) return@run 0

        // Which accounts the change touches: where the old operation sat and
        // where the new one goes.
        val affected = mutableSetOf<String>()
        for (change in changes) {
            ledger.transaction(change.oldId)?.let { affected += it.accountId }
            change.op?.let { affected += accountId(change, it) }
        }

        // The balance the bank (or the user) gave exactly, before the operations
        // counted on top of it — those are about to be re-counted. A figure from
        // a push that is no operation any more is no base (B-90).
        val all = events.all()
        val changedAt = changes.map { it.event.postedAt }.toSet()
        val withEvents = mutableSetOf<String>()
        val bases = affected.mapNotNull { id ->
            val account = ledger.account(id) ?: return@mapNotNull null
            val mine = all.mapNotNull { e ->
                val op = NotificationParser.parse(e.title, e.text, e.brand.defaultCurrency)
                    ?: return@mapNotNull null
                if (accountId(e.brand.key, op.accountCurrency) != id) null
                else RepairEvent(e.postedAt, op.balance?.minor)
            }
            if (mine.isNotEmpty()) withEvents += id
            val since = account.checkedAt?.let { ledger.sumSince(id, it) } ?: 0L
            id to RepairBase.pick(account, mine, changedAt, since)
        }.toMap()

        for (change in changes) {
            ledger.deleteTransaction(change.oldId)
            val op = change.op
            val newId = RecordNotification.eventHash(change.content, change.event.postedAt)
            if (op != null && !op.foreignAmount) {
                ledger.addTransaction(
                    Transaction(
                        id = newId,
                        brand = change.event.brand,
                        accountId = accountId(change, op),
                        amount = op.amount,
                        currency = op.currency,
                        kind = op.kind,
                        category = null,
                        counterparty = op.counterparty,
                        postedAt = change.event.postedAt
                    )
                )
            }
            events.updateParse(change.eventId, op != null, newId, change.content)
        }

        for ((id, base) in bases) {
            when (base) {
                is RepairBase.Keep -> {
                    val counted = ledger.sumSince(id, base.checkedAt)
                    ledger.setCountedBalance(id, base.balanceMinor + counted, estimated = counted != 0L)
                }
                is RepairBase.FromJournal -> {
                    ledger.setBalance(id, base.balanceMinor, base.checkedAt)
                    val counted = ledger.sumSince(id, base.checkedAt)
                    ledger.setCountedBalance(id, base.balanceMinor + counted, estimated = counted != 0L)
                }
                RepairBase.None -> {
                    val account = ledger.account(id)
                    when {
                        // Created only by a false operation: nothing left to show
                        // (a balance, a manual figure or another log entry says it was not).
                        !ledger.hasTransactions(id) -> {
                            if (account != null && account.balance.minor == 0L && account.checkedAt == null && id !in withEvents) {
                                ledger.deleteAccount(id)
                            } else if (account?.checkedAt != null) {
                                // The balance came from the false push alone: forget it, keep the account.
                                ledger.resetBalanceBase(id)
                            }
                        }
                        // Never checked: the app has no base to rebuild, as before.
                        account?.checkedAt == null -> Unit
                        // The only exact figure was false: count from the operations.
                        else -> {
                            ledger.resetBalanceBase(id)
                            ledger.setCountedBalance(id, ledger.sumSince(id, Long.MIN_VALUE), estimated = true)
                        }
                    }
                }
            }
        }
        changes.size
    }

    private fun accountId(change: Change, op: ParsedOp) = accountId(change.event.brand.key, op.accountCurrency)

    private fun accountId(brandKey: String, currency: String) = "$brandKey:$currency"

    private class Change(
        val eventId: Long,
        /** Hash the old operation was stored under — also its id. */
        val oldId: String,
        val content: String,
        val op: ParsedOp?,
        val event: ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
    )

}
