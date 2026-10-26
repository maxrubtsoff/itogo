package ru.maxrubtsoff.bankagg.domain.model

/**
 * A raw bank notification captured on the device. The raw [text] is kept
 * verbatim so an improved parser can be re-run over history. [parsed] records
 * whether an operation was successfully extracted from it.
 */
data class CapturedEvent(
    val id: Long = 0,
    val packageName: String,
    val brand: BankBrand,
    val title: String?,
    val text: String,
    val postedAt: Long,
    val dedupHash: String,
    val parsed: Boolean = false,
    /** Hash of (package, text, amount, balance) without time: repeats within a short window are one event. */
    val contentHash: String = dedupHash,
    /** Android notification key — only for dedup of pushes without a balance (B-92). */
    val notificationKey: String? = null
)
