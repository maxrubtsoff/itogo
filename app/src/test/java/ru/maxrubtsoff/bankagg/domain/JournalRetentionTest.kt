package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.JournalRetention

/** U-33: parsed texts live 90 days, unparsed — a year. */
class JournalRetentionTest {

    @Test fun cutoffs() {
        val day = 24 * 60 * 60 * 1000L
        val now = 400 * day
        assertEquals((400 - 90) * day to (400 - 365) * day, JournalRetention.cutoffs(now))
    }
}
