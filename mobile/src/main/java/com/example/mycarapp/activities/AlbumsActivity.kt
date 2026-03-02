package com.example.mycarapp.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.R
import com.example.mycarapp.Repository.AlbumsRepository
import com.example.mycarapp.adapters.AlbumsAdapter
import com.example.mycarapp.adapters.OnItemClickListener
import com.example.mycarapp.controller.ApiServiceFactory
import com.example.mycarapp.dto.Account
import com.example.mycarapp.dto.Album
import com.example.mycarapp.dto.LoginRequest
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class AlbumsActivity : AppCompatActivity(), OnItemClickListener {
    @Inject
    lateinit var configManager: ConfigManager

    @Inject
    lateinit var albumsRepository: AlbumsRepository

    @Inject
    lateinit var apiServiceFactory: ApiServiceFactory

    private lateinit var albumsRecyclerView: RecyclerView
    private lateinit var albumsAdapter: AlbumsAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var fabAddAccount: FloatingActionButton
    private lateinit var appConfig: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.albums_recycler_view)

        setupUI()
        appConfig = configManager.getConfig()

        // 1. Najpierw odczytaj i wyświetl utwory (albumy) już zapisane w bazie danych
        observeLocalAlbums()

        // 2. Następnie wykonaj logowanie i ewentualnie dodaj nowe w tle
        autoLoginAndRefresh()
    }

    private fun setupUI() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.albums_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        albumsRecyclerView = findViewById(R.id.albums_main_recycler_view)
        progressBar = findViewById(R.id.loading_progress_bar)
        statusTextView = findViewById(R.id.status_text_view)
        fabAddAccount = findViewById(R.id.fab_add_account)

        // Setup FAB click listener
        fabAddAccount.setOnClickListener {
            openAddAccountActivity()
        }
        
        // Initialize adapter with empty list
        albumsAdapter = AlbumsAdapter(emptyList(), this, this)
        albumsRecyclerView.layoutManager = LinearLayoutManager(this)
        albumsRecyclerView.adapter = albumsAdapter
    }

    private fun observeLocalAlbums() {
        lifecycleScope.launch {
            configManager.getAlbumsFlow().collectLatest { albums ->
                if (albums.isNotEmpty()) {
                    val sortedAlbums = sortAlbumsByDate(albums)
                    updateUIWithAlbums(sortedAlbums)
                } else {
                    // Jeśli nie ma nic w bazie, pokaż odpowiedni komunikat
                    if (statusTextView.visibility != View.VISIBLE) {
                        statusTextView.text = getString(R.string.no_data_found)
                        statusTextView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun autoLoginAndRefresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Spróbuj pobrać konto domyślne lub aktywne
                val account = configManager.getDefaultAccount() ?: configManager.getActiveAccount()

                if (account != null) {
                    Log.d("AlbumsActivity", "Auto-logging with account: ${account.username}")
                    refreshAccountAndLoadRemote(account)
                } else {
                    // Brak kont - przejdź do zarządzania kontami tylko jeśli baza albumów jest pusta
                    val localAlbums = configManager.getAlbumsSync()
                    if (localAlbums.isEmpty()) {
                        runOnUiThread {
                            val intent = Intent(this@AlbumsActivity, AccountsManagementActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlbumsActivity", "Error during auto-login", e)
            }
        }
    }

    private fun refreshAccountAndLoadRemote(account: Account) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = apiServiceFactory.createPublicApiService(account.serverUrl)
                val loginRequest = LoginRequest(username = account.username, password = account.password)
                val response = apiService.login(loginRequest)

                if (response.isSuccessful && response.body() != null) {
                    val loginResult = response.body()!!

                    // Zaktualizuj konto
                    val updatedAccount = account.copy(
                        authToken = loginResult.token,
                        subsonicSalt = loginResult.subsonicSalt,
                        subsonicToken = loginResult.subsonicToken
                    )
                    configManager.updateAccount(updatedAccount)

                    // Zaktualizuj konfigurację sesji
                    appConfig.authToken = loginResult.token
                    appConfig.subsonicSalt = loginResult.subsonicSalt
                    appConfig.subsonicToken = loginResult.subsonicToken
                    appConfig.serverUrl = account.serverUrl
                    appConfig.username = account.username
                    configManager.updateConfig(appConfig)

                    // Pobierz nowe albumy z serwera
                    fetchRemoteAlbums()
                }
            } catch (e: Exception) {
                Log.e("AlbumsActivity", "Background refresh failed", e)
            }
        }
    }

    private fun fetchRemoteAlbums() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val remoteAlbums = albumsRepository.getAlbums()
                if (remoteAlbums.isNotEmpty()) {
                    // Zapis do bazy automatycznie odświeży UI dzięki Flow
                    configManager.saveAlbums(remoteAlbums)
                    Log.d("AlbumsActivity", "Fetched and saved ${remoteAlbums.size} remote albums")
                }
            } catch (e: Exception) {
                Log.e("AlbumsActivity", "Error fetching remote albums", e)
            }
        }
    }

    private fun openAddAccountActivity() {
        val intent = Intent(this, AccountsManagementActivity::class.java)
        startActivity(intent)
    }

    private fun updateUIWithAlbums(albums: List<Album>) {
        runOnUiThread {
            albumsAdapter = AlbumsAdapter(albums, this@AlbumsActivity, this)
            albumsRecyclerView.adapter = albumsAdapter
            albumsRecyclerView.visibility = View.VISIBLE
            statusTextView.visibility = View.GONE
            progressBar.visibility = View.GONE
            fabAddAccount.visibility = View.VISIBLE
        }
    }

    private fun sortAlbumsByDate(albums: List<Album>): List<Album> {
        return albums.sortedWith(compareByDescending<Album> { album ->
            try {
                Instant.parse(album.createdAt).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        })
    }

    override fun onItemClick(album: Album) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("ALBUM_DATA", album)
        }
        startActivity(intent)
    }
}