package ru.maxrubtsoff.bankagg.domain.model

/**
 * How long the notification journal keeps a bank's text (U-33). Parsed ones
 * have done their job — the operation and the balance stay; unparsed ones are
 * kept longer, they are what new parser rules are written from.
 */
object JournalRetention {

    const val PARSED_DAYS = 90
    const val UNPARSED_DAYS = 365

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Events posted before these moments go: (parsed, unparsed). */
    fun cutoffs(now: Long): Pair<Long, Long> = (now - PARSED_DAYS * DAY_MS) to (now - UNPARSED_DAYS * DAY_MS)
}
