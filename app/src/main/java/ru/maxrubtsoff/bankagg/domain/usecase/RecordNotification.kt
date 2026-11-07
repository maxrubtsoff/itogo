package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.CashWithdrawal
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.parser.ParsedOp
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import ru.maxrubtsoff.bankagg.domain.model.OperationTime
import java.security.MessageDigest
import java.time.ZoneId

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
    private val tx: TransactionRunner,
    private val cash: CashRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val mutex = Mutex()

    suspend operator fun invoke(
        packageName: String,
        title: String?,
        text: String,
        postedAt: Long,
        notificationKey: String? = null
    ): Boolean = mutex.withLock {
        val normalized = text.replace(WHITESPACE, " ").trim()
        if (normalized.isEmpty()) return@withLock false

        val brand = BankBrand.fromPackage(packageName)
        // B-126: «сумма — Баланс» has no word of direction; it is a cash withdrawal only when the
        // account's balance fell by exactly that amount. The reading is under the Mutex, before any write.
        val bare = NotificationParser.parseBare(title, normalized)
        val parsed = NotificationParser.parse(title, normalized, brand.defaultCurrency)
        val op = parsed ?: bare?.let { b ->
            val prev = ledger.account("${brand.key}:${b.currency}")?.takeUnless { it.needsBalance }?.balance?.minor
            CashWithdrawal.resolve(b, prev)
        }
        // A bare push keeps one identity whether it was read or not, so a repeat of it is a repeat.
        val content = if (parsed == null && bare != null) contentHash(packageName, normalized, -bare.amountMinor, bare.balanceMinor)
            else contentHash(packageName, normalized, op)
        val hash = eventHash(content, postedAt)

        tx.run {
            // Banks re-post the same push (updates, group re-sorts): the same text,
            // amount and balance within a couple of minutes is one operation.
            if (events.hasSimilar(content, postedAt - DEDUP_WINDOW_MS, postedAt + DEDUP_WINDOW_MS, dedupKey(op, notificationKey, bare != null && parsed == null))) {
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
                    contentHash = content,
                    notificationKey = notificationKey
                )
            )
            if (!stored) return@run false

            if (op != null) {
                // The operation's own time from the text; the arrival stays the event's identity (B-123).
                val at = OperationTime.resolve(op.opTime, postedAt, zone)
                val accountId = "${brand.key}:${op.accountCurrency}"
                // An amount in another currency than the account's (1 USD, balance in EUR)
                // is not booked: only the account's own balance is taken (B-118).
                ledger.upsertAccountBalance(
                    accountId = accountId,
                    brand = brand,
                    currency = op.accountCurrency,
                    amountMinor = if (op.foreignAmount) 0L else op.amount.minor,
                    balanceMinor = op.balance?.minor,
                    postedAt = at
                )
                if (parsed == null && CashWithdrawal.isWithdrawal(op)) {
                    // The money went into the pocket: the cash grows by the amount (a hand edit of the log).
                    val taken = cash.observeChanges().first().map { it.atMs }.toSet()
                    var cashAt = at
                    while (cashAt in taken) cashAt++
                    val now = (cash.get()?.amount?.minor ?: 0L) - op.amount.minor
                    cash.addChange(CashChange(cashAt, Money.ofMinor(now), Money.ofMinor(-op.amount.minor)))
                }
                if (!op.foreignAmount) ledger.addTransaction(
                    Transaction(
                        id = hash,
                        brand = brand,
                        accountId = accountId,
                        amount = op.amount,
                        currency = op.currency,
                        kind = op.kind,
                        category = null,
                        counterparty = op.counterparty,
                        postedAt = at
                    )
                )
            }
            true
        }
    }

    companion object {
        /**
         * Key to compare in dedup: a push with a balance is told apart by its content,
         * one without — only by the notification key. null — compare the content only.
         */
        fun dedupKey(op: ParsedOp?, notificationKey: String?, bareBalance: Boolean = false): String? =
            if (op?.balance != null || bareBalance) null else notificationKey

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
