package ru.maxrubtsoff.bankagg.domain.model

/** What lies in the database file's place (B-91). */
enum class DbFile { ABSENT, PLAIN, ENCRYPTED }

/** The phone's Keystore key: there, gone for good, or only not answering right now. */
enum class KeyStatus { PRESENT, ABSENT, UNAVAILABLE }

/** Whether the stored database passphrase opens with the key. UNKNOWN is any failure that is not a clear «no». */
enum class SecretCheck { OPENS, REJECTED, NOT_STORED, UNKNOWN }

data class StorageState(
    val file: DbFile,
    val key: KeyStatus,
    val secret: SecretCheck,
    /** A copy of the passphrase sealed with the backup password exists. */
    val passwordSealStored: Boolean
)

/** What to do with the database now. */
enum class StorageDecision {
    /** Open it with the stored passphrase. */
    OPEN,
    /** Nothing to lose: a new passphrase (and, if needed, a key) is made. */
    CREATE_NEW,
    /** A plain file: encrypt it in place (a key is made if there is none). */
    CONVERT_PLAIN,
    /** Nothing to lose, but the Keystore does not answer: stay plain for now, convert later. */
    PLAIN_FALLBACK,
    /** The Keystore is only busy: do not open, do not create anything, try again. */
    WAIT,
    /** The key is lost, but the backup password still opens the data. */
    ASK_PASSWORD,
    /** The key is lost and nothing opens the data. The file stays where it is. */
    LOST,
    /** The passphrase is right, yet the file does not open. Decided by the data layer after a failed first query. */
    CORRUPT
}

enum class StorageAction { RETRY, ENTER_PASSWORD, RESTORE, START_OVER, CLOSE }

/**
 * Pure policy of B-91: a new key or passphrase is made only when there is nothing
 * to lose (no file, or a plain one); an encrypted file is never deleted, and a
 * Keystore that is merely not answering is waited out, not taken for a loss.
 */
object StorageGuard {

    fun decide(s: StorageState): StorageDecision = when (s.file) {
        DbFile.ABSENT -> when (s.key) {
            KeyStatus.UNAVAILABLE -> StorageDecision.PLAIN_FALLBACK
            else -> StorageDecision.CREATE_NEW
        }
        DbFile.PLAIN -> when (s.key) {
            KeyStatus.UNAVAILABLE -> StorageDecision.PLAIN_FALLBACK
            else -> StorageDecision.CONVERT_PLAIN
        }
        DbFile.ENCRYPTED -> when {
            s.secret == SecretCheck.OPENS -> StorageDecision.OPEN
            s.key == KeyStatus.UNAVAILABLE || s.secret == SecretCheck.UNKNOWN -> StorageDecision.WAIT
            s.passwordSealStored -> StorageDecision.ASK_PASSWORD
            else -> StorageDecision.LOST
        }
    }

    /** Only these may bring a new key or passphrase into being. */
    fun mayCreateKeyOrSecret(d: StorageDecision): Boolean =
        d == StorageDecision.CREATE_NEW || d == StorageDecision.CONVERT_PLAIN

    fun actions(d: StorageDecision): Set<StorageAction> = when (d) {
        StorageDecision.WAIT -> setOf(StorageAction.RETRY, StorageAction.CLOSE)
        StorageDecision.ASK_PASSWORD -> StorageAction.entries.toSet()
        StorageDecision.LOST, StorageDecision.CORRUPT ->
            setOf(StorageAction.RETRY, StorageAction.RESTORE, StorageAction.START_OVER, StorageAction.CLOSE)
        else -> emptySet()
    }
}
