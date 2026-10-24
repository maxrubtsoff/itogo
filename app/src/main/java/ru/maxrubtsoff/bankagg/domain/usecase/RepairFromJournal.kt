package ru.maxrubtsoff.bankagg.domain.usecase

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
        // counted on top of it — those are about to be re-counted.
        val bases = affected.mapNotNull { id ->
            val account = ledger.account(id) ?: return@mapNotNull null
            val checkedAt = account.checkedAt ?: return@mapNotNull null
            id to Base(checkedAt, account.balance.minor - ledger.sumSince(id, checkedAt))
        }.toMap()

        for (change in changes) {
            ledger.deleteTransaction(change.oldId)
            val op = change.op
            val newId = RecordNotification.eventHash(change.content, change.event.postedAt)
            if (op != null) {
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
            val counted = ledger.sumSince(id, base.checkedAt)
            ledger.setCountedBalance(id, base.balanceMinor + counted, estimated = counted != 0L)
        }
        changes.size
    }

    private fun accountId(change: Change, op: ParsedOp) = "${change.event.brand.key}:${op.currency}"

    private class Change(
        val eventId: Long,
        /** Hash the old operation was stored under — also its id. */
        val oldId: String,
        val content: String,
        val op: ParsedOp?,
        val event: ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
    )

    private class Base(val checkedAt: Long, val balanceMinor: Long)
}
