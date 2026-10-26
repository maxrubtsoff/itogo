package ru.maxrubtsoff.bankagg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :id AND postedAt > :from")
    suspend fun sumSince(id: String, from: Long): Long

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: String): AccountEntity?

    @Query("UPDATE accounts SET balanceMinor = :balanceMinor, updatedAt = :updatedAt, checkedAt = :checkedAt, estimated = :estimated WHERE id = :id")
    suspend fun setBalance(id: String, balanceMinor: Long, updatedAt: Long, checkedAt: Long?, estimated: Boolean)

    @Query("SELECT balanceMinor FROM accounts WHERE id = :id")
    suspend fun balanceOf(id: String): Long?

    @Query("SELECT updatedAt FROM accounts WHERE id = :id")
    suspend fun updatedAtOf(id: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    /** Creates the account row if missing; -1 when it already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccountIfMissing(account: AccountEntity): Long

    /** Moves the balance forward only for a newer notification; the title stays as the user left it. */
    @Query("UPDATE accounts SET balanceMinor = :balanceMinor, updatedAt = :postedAt WHERE id = :id AND updatedAt <= :postedAt")
    suspend fun updateBalanceIfNewer(id: String, balanceMinor: Long, postedAt: Long)

    @Query("UPDATE accounts SET excluded = :excluded WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean)

    @Query("UPDATE accounts SET updatedAt = MAX(updatedAt, :postedAt) WHERE id = :id")
    suspend fun touch(id: String, postedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Query("UPDATE accounts SET balanceMinor = :balanceMinor, estimated = :estimated WHERE id = :id")
    suspend fun setCountedBalance(id: String, balanceMinor: Long, estimated: Boolean)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id")
    suspend fun countTransactions(id: String): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("UPDATE accounts SET balanceMinor = 0, checkedAt = NULL, estimated = 0 WHERE id = :id")
    suspend fun resetBalanceBase(id: String)

    @Query("SELECT * FROM accounts ORDER BY brandKey, title")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM transactions ORDER BY postedAt DESC LIMIT :limit")
    fun observeTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()
}
