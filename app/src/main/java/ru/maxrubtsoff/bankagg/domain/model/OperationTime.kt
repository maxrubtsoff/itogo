package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The time an operation is booked at (B-123): the one from the push text when
 * it is plausible, else the arrival time. Plausible — not earlier than three
 * days before the arrival and not later than ten minutes after it.
 */
object OperationTime {
    private const val BEFORE_MS = 3L * 24 * 60 * 60_000
    private const val AFTER_MS = 10L * 60_000

    fun resolve(op: OpTime?, arrivedAt: Long, zone: ZoneId): Long {
        if (op == null) return arrivedAt
        val arrival = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(arrivedAt), zone)
        val limit = arrivedAt + AFTER_MS
        fun at(date: LocalDate): Long? = runCatching {
            date.atTime(op.hour, op.minute).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()

        val result: Long? = when {
            op.day != null && op.month != null -> {
                fun inYear(y: Int) = runCatching { at(LocalDate.of(y, op.month, op.day)) }.getOrNull()
                if (op.year != null) inYear(op.year)
                else inYear(arrival.year)?.let { if (it > limit) inYear(arrival.year - 1) else it }
            }
            else -> at(arrival.toLocalDate())?.let { if (it > limit) at(arrival.toLocalDate().minusDays(1)) else it }
        }
        return if (result != null && result in (arrivedAt - BEFORE_MS)..limit) result else arrivedAt
    }
}
