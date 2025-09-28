package com.example.mycarapp.HiltModule

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.mycarapp.Repository.AccountDao

import com.example.mycarapp.dto.Account
import com.example.mycarapp.dto.Album
import com.example.mycarapp.dto.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManagerImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val appConfig: AppConfig,
    private val database: AppDatabase
) : ConfigManager {

    private val accountDao: AccountDao = database.accountDao()

    init {
        loadConfigFromSharedPreferences()
    }

    override fun getConfig(): AppConfig {
        // Wczytaj aktywne konto z bazy danych
        val activeAccount = runBlocking { getActiveAccount() }

        // Zaktualizuj AppConfig danymi z aktywnego konta
        if (activeAccount != null) {
            appConfig.authToken = activeAccount.authToken
            appConfig.subsonicSalt = activeAccount.subsonicSalt
            appConfig.subsonicToken = activeAccount.subsonicToken
            appConfig.serverUrl = activeAccount.serverUrl
            appConfig.username = activeAccount.username
        }

        return appConfig
    }

    override fun updateConfig(config: AppConfig) {
        // Zaktualizuj in-memory singleton
        appConfig.authToken = config.authToken
        appConfig.subsonicSalt = config.subsonicSalt
        appConfig.subsonicToken = config.subsonicToken
        appConfig.serverUrl = config.serverUrl
        appConfig.username = config.username

        // Zapisz do SharedPreferences
        sharedPreferences.edit {
            putString(Constants.AUTH_TOKEN_KEY, config.authToken)
            putString(Constants.SUBSONIC_SALT_KEY, config.subsonicSalt)
            putString(Constants.SUBSONIC_TOKEN_KEY, config.subsonicToken)
            putString(Constants.SERVER_URL_KEY, config.serverUrl)
            putString(Constants.USERNAME_KEY, config.username)
        }
    }

    override suspend fun getActiveAccount(): Account? {
        return accountDao.getActiveAccount()
    }

    override suspend fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts()
    }

    override suspend fun addAccount(account: Account): Long {
        return accountDao.insert(account)
    }

    override suspend fun updateAccount(account: Account) {
        accountDao.update(account)
    }

    override suspend fun setActiveAccount(accountId: Int) {
        accountDao.deactivateAllAccounts()
        accountDao.activateAccount(accountId)

        // Zapisz ID aktywnego konta w SharedPreferences dla kompatybilności wstecznej
        sharedPreferences.edit {
            putInt(Constants.ACTIVE_ACCOUNT_ID_KEY, accountId)
        }
    }

    override suspend fun deleteAccount(accountId: Int) {
        accountDao.deleteAccount(accountId)
    }

    override fun clearConfig() {
        // Wyczyść in-memory singleton
        appConfig.authToken = null
        appConfig.subsonicSalt = null
        appConfig.subsonicToken = null
        appConfig.serverUrl = null
        appConfig.username = null
        appConfig.sortedAlbums = emptyList()

        // Wyczyść SharedPreferences (dla kompatybilności wstecznej)
        sharedPreferences.edit {
            remove(Constants.AUTH_TOKEN_KEY)
            remove(Constants.SUBSONIC_SALT_KEY)
            remove(Constants.SUBSONIC_TOKEN_KEY)
            remove(Constants.SERVER_URL_KEY)
            remove(Constants.USERNAME_KEY)
            remove(Constants.ACTIVE_ACCOUNT_ID_KEY)
        }
    }

    private fun loadConfigFromSharedPreferences() {
        // Dla kompatybilności wstecznej - wczytaj stare dane z SharedPreferences
        // i utwórz z nich konto w bazie danych, jeśli istnieją
        val authToken = sharedPreferences.getString(Constants.AUTH_TOKEN_KEY, null)
        val serverUrl = sharedPreferences.getString(Constants.SERVER_URL_KEY, null)
        val username = sharedPreferences.getString(Constants.USERNAME_KEY, null)

        if (authToken != null && serverUrl != null && username != null) {
            runBlocking {
                // Migruj stare dane do nowej bazy danych
                val account = Account(
                    serverUrl = serverUrl,
                    username = username,
                    password = "", // Hasło nie było przechowywane w starym systemie
                    authToken = authToken,
                    subsonicSalt = sharedPreferences.getString(Constants.SUBSONIC_SALT_KEY, null),
                    subsonicToken = sharedPreferences.getString(Constants.SUBSONIC_TOKEN_KEY, null),
                    isActive = true
                )

                val accountId = accountDao.insert(account)

                // Wyczyść stare dane z SharedPreferences
                sharedPreferences.edit {
                    remove(Constants.AUTH_TOKEN_KEY)
                    remove(Constants.SUBSONIC_SALT_KEY)
                    remove(Constants.SUBSONIC_TOKEN_KEY)
                    remove(Constants.SERVER_URL_KEY)
                    remove(Constants.USERNAME_KEY)
                }
            }
        }
    }
    override suspend fun setAsDefaultAccount(accountId: Int) {
        accountDao.clearAllDefaultFlags()
        accountDao.setAsDefault(accountId)

        // Opcjonalnie: ustaw też jako aktywne
        setActiveAccount(accountId)
    }

    override suspend fun getDefaultAccount(): Account? {
        return accountDao.getDefaultAccount()
    }

    override fun setSortedAlbums(albums: List<Album>) {
        appConfig.sortedAlbums = albums
        // Opcjonalnie: zapisz do SharedPreferences jeśli potrzebujesz trwałości
        sharedPreferences.edit {
            // Możesz zapisać ilość albumów dla informacji
            putInt("sorted_albums_count", albums.size)
        }
    }

    override fun getSortedAlbums(): List<Album> {
        return appConfig.sortedAlbums
    }
}