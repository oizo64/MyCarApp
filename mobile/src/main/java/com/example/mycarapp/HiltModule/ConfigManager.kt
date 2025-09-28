package com.example.mycarapp.HiltModule

import com.example.mycarapp.dto.Account
import com.example.mycarapp.dto.Album
import kotlinx.coroutines.flow.Flow

interface ConfigManager {
    fun getConfig(): AppConfig
    fun updateConfig(config: AppConfig)
    suspend fun getActiveAccount(): Account?
    suspend fun getAllAccounts(): Flow<List<Account>>
    suspend fun addAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun setActiveAccount(accountId: Int)
    suspend fun deleteAccount(accountId: Int)
    suspend fun setAsDefaultAccount(accountId: Int)
    suspend fun getDefaultAccount(): Account?
    fun setSortedAlbums(albums: List<Album>)
    fun getSortedAlbums(): List<Album>
    fun clearConfig()
}