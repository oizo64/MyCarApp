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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class AlbumsActivity : AppCompatActivity(), OnItemClickListener {

    private lateinit var albumsRecyclerView: RecyclerView
    private lateinit var albumsAdapter: AlbumsAdapter
    private lateinit var albumsRepository: AlbumsRepository
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView

    private lateinit var authToken: String
    private lateinit var subsonicSalt: String
    private lateinit var subsonicToken: String
    private lateinit var serverUrl: String
    private lateinit var username: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.albums_recycler_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.albums_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        authToken = intent?.getStringExtra("AUTH_TOKEN") ?: ""
        subsonicSalt = intent?.getStringExtra("SUBSONIC_SALT") ?: ""
        subsonicToken = intent?.getStringExtra("SUBSONIC_TOKEN") ?: ""
        serverUrl = intent?.getStringExtra("SERVER_URL") ?: ""
        username = intent?.getStringExtra("USERNAME") ?: ""


        albumsRecyclerView = findViewById(R.id.albums_main_recycler_view)
        progressBar = findViewById(R.id.loading_progress_bar)
        statusTextView = findViewById(R.id.status_text_view)

        albumsRepository =
            RemoteAlbumsRepository(authToken, subsonicSalt, subsonicToken, serverUrl, username)

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            albumsRecyclerView.visibility = View.GONE
            statusTextView.visibility = View.GONE

            try {
                // Pobranie danych z serwisu
                val unsortedAlbumsList = albumsRepository.getAlbums()

                // Sortowanie listy po dacie createdAt, od najnowszych
                val sortedAlbumsList = unsortedAlbumsList.sortedByDescending { album ->
                    try {
                        // Tworzenie formattera do parsowania daty
                        val parser = SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'",
                            Locale.getDefault()
                        )
                        parser.parse(album.createdAt) // Parsowanie daty
                    } catch (e: Exception) {
                        Log.e("SORT_DATE", "Błąd parsowania daty: ${album.createdAt}", e)
                        null // Zwracamy null w przypadku błędu, co umieści element na końcu
                    }
                }

                if (sortedAlbumsList.isNotEmpty()) {
                    albumsAdapter = AlbumsAdapter(sortedAlbumsList, this@AlbumsActivity)
                    albumsRecyclerView.layoutManager = LinearLayoutManager(this@AlbumsActivity)
                    albumsRecyclerView.adapter = albumsAdapter
                    albumsRecyclerView.visibility = View.VISIBLE
                } else {
                    statusTextView.text =
                        getString(R.string.no_data_found) // Gdzie 'no_data_found' jest zdefiniowane w strings.xml                    statusTextView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                statusTextView.text = getString(R.string.status_error_with_message, e.message)
                statusTextView.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onItemClick(album: Album) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("ALBUM_DATA", album)
            putExtra("SERVER_URL", serverUrl)
            putExtra("USERNAME", username)
            putExtra("SUBSONIC_TOKEN", subsonicToken)
            putExtra("SUBSONIC_SALT", subsonicSalt)
            putExtra("AUTH_TOKEN", authToken)
        }
        startActivity(intent)
    }

}