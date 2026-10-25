package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BackupRotation
import ru.maxrubtsoff.bankagg.domain.model.CopyKind
import ru.maxrubtsoff.bankagg.domain.model.CopyName
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BackupRotationTest {

    private val d = LocalDate.of(2026, 10, 22)

    @Test fun namesRoundTrip() {
        val at = LocalDateTime.of(2026, 10, 22, 21, 42)
        assertEquals(CopyName(d, null, CopyKind.AUTO, true), BackupRotation.parse(BackupRotation.autoName(d)))
        assertEquals(CopyName(d, LocalTime.of(21, 42), CopyKind.MANUAL, true), BackupRotation.parse(BackupRotation.manualName(at)))
        assertEquals(CopyName(d, LocalTime.of(21, 42), CopyKind.BEFORE_RESTORE, true), BackupRotation.parse(BackupRotation.beforeName(at)))
        assertEquals(CopyName(d, LocalTime.of(21, 42), CopyKind.MANUAL, true), BackupRotation.parse("itogo-2026-10-22-2142-manual (1).itogo"))
    }

    @Test fun oldPlainNames() {
        assertEquals(CopyName(d, null, CopyKind.AUTO, false), BackupRotation.parse("bankagg-2026-10-22.json"))
        assertEquals(d, BackupRotation.dayOf("bankagg-2026-10-22 (1).json"))
        assertEquals(CopyName(LocalDate.of(2026, 10, 21), LocalTime.of(19, 50), CopyKind.MANUAL, false), BackupRotation.parse("bankagg-backup-2026-10-21-1950.json"))
        assertNull(BackupRotation.parse("photo.jpg"))
        assertNull(BackupRotation.parse("itogo-2026-13-40.itogo"))
    }

    @Test fun everyKindLivesSevenDays() {
        val names = (1..10).map { "itogo-2026-10-%02d.itogo".format(it) } +
            "itogo-2026-10-02-1200-manual.itogo" + "itogo-2026-10-09-1800-before.itogo"
        assertEquals(
            listOf("itogo-2026-10-01.itogo", "itogo-2026-10-02.itogo", "itogo-2026-10-03.itogo", "itogo-2026-10-02-1200-manual.itogo"),
            BackupRotation.toDelete(names)
        )
    }

    @Test fun plainCopiesGoOnceAnEncryptedOneExists() {
        val old = listOf("bankagg-2026-10-20.json", "bankagg-backup-2026-10-21-1950.json")
        assertEquals(emptyList<String>(), BackupRotation.toDelete(old))
        assertEquals(old, BackupRotation.toDelete(old + "itogo-2026-10-22.itogo"))
        // A manual encrypted copy alone isn't enough: the automatic one must exist first.
        assertEquals(emptyList<String>(), BackupRotation.toDelete(old + "itogo-2026-10-22-1200-manual.itogo"))
    }

    @Test fun relativeDays() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals("сегодня", RuDays.relative(today, today))
        assertEquals("вчера", RuDays.relative(today.minusDays(1), today))
        assertEquals("15 октября", RuDays.relative(LocalDate.of(2026, 10, 15), today))
        assertEquals("31 декабря 2025", RuDays.relative(LocalDate.of(2025, 12, 31), today))
    }

    @Test fun weekdayNamesAllSevenDays() {
        // 2026-10-19 is a Monday: the week from its start to its end, then the next one begins again.
        val monday = LocalDate.of(2026, 10, 19)
        val names = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
        names.forEachIndexed { i, n -> assertEquals(n, RuDays.weekday(monday.plusDays(i.toLong()))) }
        assertEquals("понедельник", RuDays.weekday(monday.plusDays(7)))
        assertEquals("воскресенье", RuDays.weekday(monday.minusDays(1)))
        // Across a year boundary: 2026-01-01 is a Thursday.
        assertEquals("четверг", RuDays.weekday(LocalDate.of(2026, 1, 1)))
    }
}
