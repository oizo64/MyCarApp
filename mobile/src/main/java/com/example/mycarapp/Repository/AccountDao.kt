package com.example.mycarapp.Repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mycarapp.dto.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("SELECT * FROM accounts ORDER BY id ASC") // lub DESC
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccount(): Account?

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun deactivateAllAccounts()

    @Query("UPDATE accounts SET isActive = 1 WHERE id = :accountId")
    suspend fun activateAccount(accountId: Int)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccount(accountId: Int)

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearAllDefaultFlags()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :accountId")
    suspend fun setAsDefault(accountId: Int)

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): Account?
}