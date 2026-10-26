package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BackupGuard
import ru.maxrubtsoff.bankagg.domain.model.CopyStats

class BackupGuardTest {

    @Test fun empty_fresh_does_not_overwrite_nonempty_copy() {
        assertFalse(BackupGuard.mayOverwrite(CopyStats(12), CopyStats(0)))
    }

    @Test fun nonempty_overwrites() {
        assertTrue(BackupGuard.mayOverwrite(CopyStats(12), CopyStats(3)))
        assertTrue(BackupGuard.mayOverwrite(null, CopyStats(3)))
    }

    @Test fun empty_over_empty_allowed() {
        assertTrue(BackupGuard.mayOverwrite(CopyStats(0), CopyStats(0)))
        assertTrue(BackupGuard.mayOverwrite(null, CopyStats(0)))
    }

    @Test fun empty_does_not_rotate_old_copies() {
        assertFalse(BackupGuard.mayRotate(CopyStats(0)))
        assertTrue(BackupGuard.mayRotate(CopyStats(1)))
    }
}
