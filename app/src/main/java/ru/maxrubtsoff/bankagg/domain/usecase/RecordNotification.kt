package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.parser.ParsedOp
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import java.security.MessageDigest

/**
 * Handles one incoming bank notification entirely on-device: parses it, stores
 * the raw event for the log, and (when parsed) updates the account balance and
 * records the transaction. Returns true when a new event was stored.
 *
 * Notifications are processed one at a time ([Mutex]) and each one is written
 * in a single transaction, so two pushes can't race on a balance and a crash
 * mid-way can't leave an event stored without its operation (which the
 * de-duplication would then block forever).
 */
class RecordNotification(
    private val events: EventRepository,
    private val ledger: LedgerRepository,
    private val tx: TransactionRunner
) {
    private val mutex = Mutex()

    suspend operator fun invoke(
        packageName: String,
        title: String?,
        text: String,
        postedAt: Long
    ): Boolean = mutex.withLock {
        val normalized = text.replace(WHITESPACE, " ").trim()
        if (normalized.isEmpty()) return@withLock false

        val brand = BankBrand.fromPackage(packageName)
        val op = NotificationParser.parse(title, normalized, brand.defaultCurrency)
        val content = contentHash(packageName, normalized, op)
        val hash = eventHash(content, postedAt)

        tx.run {
            // Banks re-post the same push (updates, group re-sorts): the same text,
            // amount and balance within a couple of minutes is one operation.
            if (events.hasSimilar(content, postedAt - DEDUP_WINDOW_MS, postedAt + DEDUP_WINDOW_MS)) {
                return@run false
            }
            val stored = events.record(
                CapturedEvent(
                    packageName = packageName,
                    brand = brand,
                    title = title?.trim()?.ifEmpty { null },
                    text = normalized,
                    postedAt = postedAt,
                    dedupHash = hash,
                    parsed = op != null,
                    contentHash = content
                )
            )
            if (!stored) return@run false

            if (op != null) {
                val accountId = "${brand.key}:${op.currency}"
                ledger.upsertAccountBalance(
                    accountId = accountId,
                    brand = brand,
                    currency = op.currency,
                    amountMinor = op.amount.minor,
                    balanceMinor = op.balance?.minor,
                    postedAt = postedAt
                )
                ledger.addTransaction(
                    Transaction(
                        id = hash,
                        brand = brand,
                        accountId = accountId,
                        amount = op.amount,
                        currency = op.currency,
                        kind = op.kind,
                        category = null,
                        counterparty = op.counterparty,
                        postedAt = postedAt
                    )
                )
            }
            true
        }
    }

    companion object {
        /** Window in which an identical push counts as a repeat (±2 minutes). */
        const val DEDUP_WINDOW_MS = 2 * 60_000L

        private val WHITESPACE = Regex("\\s+")

        /** Identity of a push without its time: package, text, amount and balance. */
        fun contentHash(packageName: String, normalizedText: String, op: ParsedOp?): String =
            contentHash(packageName, normalizedText, op?.amount?.minor, op?.balance?.minor)

        fun contentHash(
            packageName: String,
            normalizedText: String,
            amountMinor: Long?,
            balanceMinor: Long?
        ): String = sha256("$packageName|$normalizedText|${amountMinor ?: ""}|${balanceMinor ?: ""}")

        /** Identity of this very push: its content and the time it arrived. Also the operation's id. */
        fun eventHash(contentHash: String, postedAt: Long): String = sha256("$contentHash|$postedAt")

        private fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
