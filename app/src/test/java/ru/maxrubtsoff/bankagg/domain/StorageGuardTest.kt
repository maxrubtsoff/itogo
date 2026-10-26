package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.DbFile
import ru.maxrubtsoff.bankagg.domain.model.KeyStatus
import ru.maxrubtsoff.bankagg.domain.model.SecretCheck
import ru.maxrubtsoff.bankagg.domain.model.StorageAction
import ru.maxrubtsoff.bankagg.domain.model.StorageDecision
import ru.maxrubtsoff.bankagg.domain.model.StorageGuard
import ru.maxrubtsoff.bankagg.domain.model.StorageState

class StorageGuardTest {

    private fun decide(file: DbFile, key: KeyStatus, secret: SecretCheck = SecretCheck.NOT_STORED, seal: Boolean = false) =
        StorageGuard.decide(StorageState(file, key, secret, seal))

    @Test fun fresh_install_key_present_creates_new() {
        assertEquals(StorageDecision.CREATE_NEW, decide(DbFile.ABSENT, KeyStatus.PRESENT))
    }

    @Test fun fresh_install_without_key_yet_creates_new() {
        assertEquals(StorageDecision.CREATE_NEW, decide(DbFile.ABSENT, KeyStatus.ABSENT))
    }

    @Test fun fresh_install_keystore_down_falls_back_plain() {
        assertEquals(StorageDecision.PLAIN_FALLBACK, decide(DbFile.ABSENT, KeyStatus.UNAVAILABLE))
    }

    @Test fun no_file_stale_secret_is_replaced() {
        assertEquals(StorageDecision.CREATE_NEW, decide(DbFile.ABSENT, KeyStatus.PRESENT, SecretCheck.REJECTED))
        assertEquals(StorageDecision.CREATE_NEW, decide(DbFile.ABSENT, KeyStatus.ABSENT, SecretCheck.REJECTED, seal = true))
    }

    @Test fun encrypted_file_secret_opens_is_open() {
        assertEquals(StorageDecision.OPEN, decide(DbFile.ENCRYPTED, KeyStatus.PRESENT, SecretCheck.OPENS))
    }

    @Test fun encrypted_file_keystore_unavailable_waits_never_creates() {
        for (secret in SecretCheck.entries.filter { it != SecretCheck.OPENS }) {
            val d = decide(DbFile.ENCRYPTED, KeyStatus.UNAVAILABLE, secret, seal = true)
            assertEquals(StorageDecision.WAIT, d)
            assertFalse(StorageGuard.mayCreateKeyOrSecret(d))
        }
    }

    @Test fun encrypted_file_secret_unknown_waits() {
        assertEquals(StorageDecision.WAIT, decide(DbFile.ENCRYPTED, KeyStatus.PRESENT, SecretCheck.UNKNOWN))
    }

    @Test fun encrypted_file_key_absent_with_stored_secret_is_lost_not_create() {
        val d = decide(DbFile.ENCRYPTED, KeyStatus.ABSENT, SecretCheck.REJECTED)
        assertEquals(StorageDecision.LOST, d)
        assertFalse(StorageGuard.mayCreateKeyOrSecret(d))
    }

    @Test fun encrypted_file_secret_rejected_without_password_seal_is_lost() {
        assertEquals(StorageDecision.LOST, decide(DbFile.ENCRYPTED, KeyStatus.PRESENT, SecretCheck.REJECTED))
    }

    @Test fun encrypted_file_secret_rejected_with_password_seal_asks_password() {
        assertEquals(StorageDecision.ASK_PASSWORD, decide(DbFile.ENCRYPTED, KeyStatus.PRESENT, SecretCheck.REJECTED, seal = true))
        assertEquals(StorageDecision.ASK_PASSWORD, decide(DbFile.ENCRYPTED, KeyStatus.ABSENT, SecretCheck.REJECTED, seal = true))
    }

    @Test fun encrypted_file_without_stored_secret_is_lost_never_fresh_secret() {
        for (key in listOf(KeyStatus.PRESENT, KeyStatus.ABSENT)) {
            val d = decide(DbFile.ENCRYPTED, key, SecretCheck.NOT_STORED)
            assertEquals(StorageDecision.LOST, d)
            assertFalse(StorageGuard.mayCreateKeyOrSecret(d))
        }
    }

    @Test fun plain_file_key_present_converts() {
        assertEquals(StorageDecision.CONVERT_PLAIN, decide(DbFile.PLAIN, KeyStatus.PRESENT))
    }

    @Test fun plain_file_key_absent_converts_with_a_new_key() {
        // A plain file holds nothing a new key could lose.
        assertEquals(StorageDecision.CONVERT_PLAIN, decide(DbFile.PLAIN, KeyStatus.ABSENT))
    }

    @Test fun plain_file_keystore_down_stays_plain() {
        assertEquals(StorageDecision.PLAIN_FALLBACK, decide(DbFile.PLAIN, KeyStatus.UNAVAILABLE))
    }

    @Test fun only_create_new_and_convert_plain_may_create_key() {
        val allowed = StorageDecision.entries.filter { StorageGuard.mayCreateKeyOrSecret(it) }.toSet()
        assertEquals(setOf(StorageDecision.CREATE_NEW, StorageDecision.CONVERT_PLAIN), allowed)
    }

    @Test fun encrypted_file_never_decides_to_create() {
        for (key in KeyStatus.entries) for (secret in SecretCheck.entries) for (seal in listOf(false, true)) {
            assertFalse(StorageGuard.mayCreateKeyOrSecret(decide(DbFile.ENCRYPTED, key, secret, seal)))
        }
    }

    @Test fun start_over_never_offered_while_waiting_or_open() {
        for (d in listOf(StorageDecision.WAIT, StorageDecision.OPEN, StorageDecision.CREATE_NEW)) {
            assertFalse(StorageAction.START_OVER in StorageGuard.actions(d))
        }
        assertTrue(StorageAction.START_OVER in StorageGuard.actions(StorageDecision.LOST))
    }

    @Test fun wait_offers_only_retry_and_close() {
        assertEquals(setOf(StorageAction.RETRY, StorageAction.CLOSE), StorageGuard.actions(StorageDecision.WAIT))
    }

    @Test fun password_offered_only_when_a_password_seal_exists() {
        assertTrue(StorageAction.ENTER_PASSWORD in StorageGuard.actions(StorageDecision.ASK_PASSWORD))
        assertFalse(StorageAction.ENTER_PASSWORD in StorageGuard.actions(StorageDecision.LOST))
        assertFalse(StorageAction.ENTER_PASSWORD in StorageGuard.actions(StorageDecision.CORRUPT))
    }
}
