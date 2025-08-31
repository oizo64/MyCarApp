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
import com.example.mycarapp.R
import com.example.mycarapp.Repository.AlbumsRepository
import com.example.mycarapp.Repository.RemoteAlbumsRepository
import com.example.mycarapp.adapters.AlbumsAdapter
import com.example.mycarapp.adapters.OnItemClickListener
import com.example.mycarapp.controller.ApiService
import com.example.mycarapp.dto.Album
import com.example.mycarapp.dto.LoginRequest
import com.example.mycarapp.dto.LoginResponse
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.MainActivity
import com.example.mycarapp.dto.Account
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlbumsActivity : AppCompatActivity(), OnItemClickListener {
    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var albumsRecyclerView: RecyclerView
    private lateinit var albumsAdapter: AlbumsAdapter
    private lateinit var albumsRepository: AlbumsRepository
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var fabAddAccount: FloatingActionButton
    private lateinit var appConfig: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.albums_recycler_view)

        setupUI()
        appConfig = configManager.getConfig()

        // ZMIEŃ TĘ LOGIKĘ - najpierw sprawdź czy użytkownik jest zalogowany
        if (isUserLoggedIn()) {
            initializeRepository()
            loadAlbums()
        } else {
            // Tylko jeśli nie ma zalogowanego użytkownika, spróbuj automatyczne logowanie
            autoLoginWithStoredCredentials()
        }
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
    }

    private fun openAddAccountActivity() {
        val intent = Intent(this, AccountsManagementActivity::class.java)
        startActivity(intent)
    }

    private fun isUserLoggedIn(): Boolean {
        return !appConfig.authToken.isNullOrEmpty() && !appConfig.serverUrl.isNullOrEmpty()
    }

    private fun autoLoginWithStoredCredentials() {
        lifecycleScope.launch(Dispatchers.IO) {
            runOnUiThread {
                showLoadingState()
                statusTextView.text = getString(R.string.status_connecting)
            }

            try {
                // 1. Spróbuj pobrać konto domyślne z bazy
                val defaultAccount = configManager.getDefaultAccount()

                if (defaultAccount != null) {
                    // Użyj konta domyślnego
                    Log.d("AlbumsActivity", "Using default account: ${defaultAccount.username}")
                    useAccount(defaultAccount)
                    return@launch
                }

                // 2. Jeśli nie ma domyślnego, spróbuj pobrać aktywne konto
                val activeAccount = configManager.getActiveAccount()
                if (activeAccount != null) {
                    Log.d("AlbumsActivity", "Using active account: ${activeAccount.username}")
                    useAccount(activeAccount)
                    return@launch
                }

                // 3. Jeśli nie ma żadnego konta, przejdź do zarządzania kontami
                runOnUiThread {
                    statusTextView.text = getString(R.string.no_accounts_available)
                    val intent = Intent(this@AlbumsActivity, AccountsManagementActivity::class.java)
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e("AlbumsActivity", "Error loading default account", e)
                runOnUiThread {
                    statusTextView.text = getString(R.string.status_network_error)
                    showManualLoginRequired()
                }
            }
        }
    }
    private fun useAccount(account: Account) {
        // Zaktualizuj konfigurację z konta
        appConfig.authToken = account.authToken
        appConfig.subsonicSalt = account.subsonicSalt
        appConfig.subsonicToken = account.subsonicToken
        appConfig.serverUrl = account.serverUrl
        appConfig.username = account.username

        // Zapisz konfigurację
        configManager.updateConfig(appConfig)

        runOnUiThread {
            statusTextView.text = getString(R.string.status_login_success)
            initializeRepository()
            loadAlbums()
        }
    }

    private fun createApiService(serverUrl: String): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private fun handleSuccessfulLogin(loginResult: LoginResponse, serverUrl: String, username: String) {
        // Zaktualizuj konfigurację
        appConfig.authToken = loginResult.token
        appConfig.subsonicSalt = loginResult.subsonicSalt
        appConfig.subsonicToken = loginResult.subsonicToken
        appConfig.serverUrl = serverUrl
        appConfig.username = username

        // Zapisz konfigurację
        configManager.updateConfig(appConfig)

        runOnUiThread {
            statusTextView.text = getString(R.string.status_login_success)
            initializeRepository()
            loadAlbums()
        }
    }

    private fun handleLoginError(code: Int, message: String?) {
        Log.e("API_AUTH", "Błąd logowania: $code - $message")
        runOnUiThread {
            statusTextView.text = getString(R.string.status_login_error)
        }
    }

    private fun handleNetworkError(e: Exception) {
        Log.e("API_AUTH", "Wyjątek podczas logowania:", e)
        runOnUiThread {
            statusTextView.text = getString(R.string.status_network_error)
        }
    }

    private fun showManualLoginRequired() {
        runOnUiThread {
            statusTextView.text = getString(R.string.status_manual_login_required)
        }
    }

    private fun redirectToManualLogin() {
        val intent = Intent(this@AlbumsActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun initializeRepository() {
        albumsRepository = RemoteAlbumsRepository(
            authToken = appConfig.authToken!!,
            subsonicSalt = appConfig.subsonicSalt ?: "",
            subsonicToken = appConfig.subsonicToken ?: "",
            serverUrl = appConfig.serverUrl!!,
            username = appConfig.username ?: ""
        )
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            showLoadingState()
            try {
                val unsortedAlbumsList = albumsRepository.getAlbums()
                val sortedAlbumsList = sortAlbumsByDate(unsortedAlbumsList)
                appConfig.sortedAlbums = sortedAlbumsList
                updateUIWithAlbums(sortedAlbumsList)
            } catch (e: Exception) {
                showErrorState(e.message)
                Log.e("ALBUMS_LOAD", "Błąd ładowania albumów", e)
            } finally {
                hideLoadingState()
            }
        }
    }

    private fun showLoadingState() {
        progressBar.visibility = View.VISIBLE
        albumsRecyclerView.visibility = View.GONE
        statusTextView.visibility = View.VISIBLE
        fabAddAccount.visibility = View.GONE // Hide FAB during loading
    }

    private fun hideLoadingState() {
        progressBar.visibility = View.GONE
        fabAddAccount.visibility = View.VISIBLE // Show FAB after loading
    }

    private fun updateUIWithAlbums(albums: List<Album>) {
        if (albums.isNotEmpty()) {
            albumsAdapter = AlbumsAdapter(albums, this@AlbumsActivity)
            albumsRecyclerView.layoutManager = LinearLayoutManager(this@AlbumsActivity)
            albumsRecyclerView.adapter = albumsAdapter
            albumsRecyclerView.visibility = View.VISIBLE
            statusTextView.visibility = View.GONE
            fabAddAccount.visibility = View.VISIBLE // Show FAB when albums are loaded
        } else {
            statusTextView.text = getString(R.string.no_data_found)
            statusTextView.visibility = View.VISIBLE
            fabAddAccount.visibility = View.VISIBLE // Show FAB even if no albums
        }
    }

    private fun showErrorState(message: String?) {
        statusTextView.text = getString(R.string.status_error_with_message, message)
        statusTextView.visibility = View.VISIBLE
        fabAddAccount.visibility = View.VISIBLE // Show FAB even on error
    }

    private fun sortAlbumsByDate(albums: List<Album>): List<Album> {
        return albums.sortedByDescending { album ->
            try {
                val parser = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'",
                    Locale.getDefault()
                )
                parser.parse(album.createdAt)
            } catch (e: Exception) {
                Log.e("SORT_DATE", "Błąd parsowania daty: ${album.createdAt}", e)
                Date(0)
            }
        }
    }

    override fun onItemClick(album: Album) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("ALBUM_DATA", album)
        }
        startActivity(intent)
    }
}