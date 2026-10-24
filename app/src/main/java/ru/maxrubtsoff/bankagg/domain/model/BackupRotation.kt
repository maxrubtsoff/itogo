package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What made a copy (U-30): the app by itself, «Сохранить сейчас», or a restore about to replace the data (B-26). */
enum class CopyKind { AUTO, MANUAL, BEFORE_RESTORE }

/** What a backup file name says about it. [sealed] — encrypted (F-26); old copies are plain JSON. */
data class CopyName(val day: LocalDate, val time: LocalTime?, val kind: CopyKind, val sealed: Boolean)

/**
 * The copies in Downloads/BankAggregator. The automatic one is one file per day,
 * rewritten as data changes; «Сохранить сейчас» and «до восстановления» are
 * separate files with their time, never rewritten. Every kind lives
 * [KEEP_DAYS] days, so a mistake that reached today's copy can still be undone
 * from an earlier one.
 */
object BackupRotation {

    const val KEEP_DAYS = 7

    private const val EXT = ".itogo"
    private val NEW = Regex("""^itogo-(\d{4})-(\d{2})-(\d{2})(?:-(\d{2})(\d{2})-(manual|before))?(?: \(\d+\))?\.itogo$""")
    // Before F-26: the daily copy and manual copies of older versions, plain JSON.
    private val DAILY = Regex("""^bankagg-(\d{4})-(\d{2})-(\d{2})(?: \(\d+\))?\.json$""")
    private val LEGACY = Regex("""^bankagg-backup-(\d{4})-(\d{2})-(\d{2})-(\d{2})(\d{2})(?: \(\d+\))?\.json$""")

    fun autoName(day: LocalDate): String = "itogo-$day$EXT"
    fun manualName(at: LocalDateTime): String = "itogo-${at.toLocalDate()}-%02d%02d-manual$EXT".format(at.hour, at.minute)
    fun beforeName(at: LocalDateTime): String = "itogo-${at.toLocalDate()}-%02d%02d-before$EXT".format(at.hour, at.minute)
    /** A plain copy, only when the phone's key doesn't work. */
    fun plainName(day: LocalDate): String = "bankagg-$day.json"

    fun parse(name: String): CopyName? = runCatching {
        NEW.matchEntire(name)?.let { m ->
            val g = m.groupValues
            val kind = when (g[6]) { "manual" -> CopyKind.MANUAL; "before" -> CopyKind.BEFORE_RESTORE; else -> CopyKind.AUTO }
            val time = if (g[4].isEmpty()) null else LocalTime.of(g[4].toInt(), g[5].toInt())
            return@runCatching CopyName(LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()), time, kind, sealed = true)
        }
        DAILY.matchEntire(name)?.let { m ->
            val g = m.groupValues
            return@runCatching CopyName(LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()), null, CopyKind.AUTO, sealed = false)
        }
        LEGACY.matchEntire(name)?.let { m ->
            val g = m.groupValues
            return@runCatching CopyName(LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()), LocalTime.of(g[4].toInt(), g[5].toInt()), CopyKind.MANUAL, sealed = false)
        }
        null
    }.getOrNull()

    /** The day a backup file is from, or null for anything else in the folder. */
    fun dayOf(name: String): LocalDate? = parse(name)?.day

    /**
     * Copies to remove: any beyond the newest [keep] days, and — once an
     * encrypted automatic copy exists — every old plain one (F-26): a readable
     * file in Downloads is exactly what encryption is for.
     */
    fun toDelete(names: List<String>, keep: Int = KEEP_DAYS): List<String> {
        val parsed = names.mapNotNull { n -> parse(n)?.let { n to it } }
        val keptDays = parsed.map { it.second.day }.distinct().sortedDescending().take(keep).toSet()
        val sealedAuto = parsed.any { it.second.sealed && it.second.kind == CopyKind.AUTO }
        return parsed.filter { (_, c) -> c.day !in keptDays || (sealedAuto && !c.sealed) }.map { it.first }
    }
}

/** "сегодня", "вчера", "15 октября", "15 октября 2025" — how a day reads in the UI. */
object RuDays {
    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    fun relative(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> "${day.dayOfMonth} ${MONTHS[day.monthValue - 1]}" + if (day.year != today.year) " ${day.year}" else ""
    }
}
