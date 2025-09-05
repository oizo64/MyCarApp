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
import com.example.mycarapp.dto.Album
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    private lateinit var appConfig: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.albums_recycler_view)

        setupUI()

        appConfig = configManager.getConfig()
        if (!checkUserLoginState()) {
            return
        }

        initializeRepository()
        loadAlbums()
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
    }

    private fun checkUserLoginState(): Boolean {
        if (appConfig.authToken.isNullOrEmpty() || appConfig.serverUrl.isNullOrEmpty()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return false
        }
        return true
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
        statusTextView.visibility = View.GONE
    }

    private fun hideLoadingState() {
        progressBar.visibility = View.GONE
    }

    private fun updateUIWithAlbums(albums: List<Album>) {
        if (albums.isNotEmpty()) {
            albumsAdapter = AlbumsAdapter(albums, this@AlbumsActivity)
            albumsRecyclerView.layoutManager = LinearLayoutManager(this@AlbumsActivity)
            albumsRecyclerView.adapter = albumsAdapter
            albumsRecyclerView.visibility = View.VISIBLE
        } else {
            statusTextView.text = getString(R.string.no_data_found)
            statusTextView.visibility = View.VISIBLE
        }
    }

    private fun showErrorState(message: String?) {
        statusTextView.text = getString(R.string.status_error_with_message, message)
        statusTextView.visibility = View.VISIBLE
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
                Date(0) // Używamy bardzo starej daty, aby element trafił na koniec listy
            }
        }
    }

    override fun onItemClick(album: Album) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("ALBUM_DATA", album)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
    }
}