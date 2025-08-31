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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.albums_recycler_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.albums_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val authToken = intent.getStringExtra("AUTH_TOKEN")
        val subsonicSalt = intent.getStringExtra("SUBSONIC_SALT")
        val subsonicToken = intent.getStringExtra("SUBSONIC_TOKEN")
        val serverUrl = intent.getStringExtra("SERVER_URL")
        val username = intent.getStringExtra("USERNAME")

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
                    statusTextView.text = "Nie znaleziono albumów."
                    statusTextView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                statusTextView.text = "Wystąpił błąd: ${e.message}"
                statusTextView.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onItemClick(album: Album) {
        // Tworzymy Intent, aby przejść do PlayerActivity
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("ALBUM_ID", album.id)
            // Możesz przekazać cały obiekt Album, jeśli jest Parcelable
            putExtra("ALBUM_DATA", album)
        }
        startActivity(intent)
    }

}