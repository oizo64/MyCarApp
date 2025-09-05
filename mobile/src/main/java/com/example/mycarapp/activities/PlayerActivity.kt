package com.example.mycarapp.activities

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.R
import com.example.mycarapp.controller.ApiService
import com.example.mycarapp.dto.Album
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    @Inject
    lateinit var configManager: ConfigManager
    private lateinit var playPauseButton: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeTextView: TextView
    private lateinit var totalTimeTextView: TextView
    private lateinit var appConfig: AppConfig
    // Stan odtwarzacza
    private var isPlaying = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var totalDuration: Long = 0
    private var streamUrl: String? = null
    private val SKIP_TIME_MS = 60000

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                val currentPosition = player.currentPosition // ✅ Pobierz rzeczywistą pozycję
                seekBar.progress = currentPosition
                currentTimeTextView.text = formatTime(currentPosition.toLong())
            }

            if (isPlaying.get()) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playPauseButton = findViewById(R.id.play_pause_button)
        seekBar = findViewById(R.id.player_seek_bar)
        currentTimeTextView = findViewById(R.id.current_time_text_view)
        totalTimeTextView = findViewById(R.id.total_time_text_view)
        val previousButton: ImageButton = findViewById(R.id.previous_button)
        val nextButton: ImageButton = findViewById(R.id.next_button)
        val albumCoverImageView: ImageView = findViewById(R.id.player_album_cover_image_view)
        val albumNameTextView: TextView = findViewById(R.id.player_album_name_text_view)
        val albumArtistTextView: TextView = findViewById(R.id.player_album_artist_text_view)
        val albumCreatedTextView: TextView = findViewById(R.id.player_album_created_text_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Pobranie konfiguracji z ConfigManager
        appConfig = configManager.getConfig()
        val album = intent.getParcelableExtra<Album>("ALBUM_DATA")
        val serverUrl = appConfig.serverUrl
        val username = appConfig.username
        val subsonicToken = appConfig.subsonicToken
        val subsonicSalt = appConfig.subsonicSalt
        val authToken = appConfig.authToken

        var playedSongId: String?

        if (album == null || serverUrl == null || username == null || subsonicToken == null || subsonicSalt == null) {
            Log.e("PlayerActivity", "Błąd: Brak wszystkich danych do odtwarzania.")
            return
        }

        // 5. Wyłącz przycisk play
        playPauseButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val authInterceptor = Interceptor { chain ->
                    val originalRequest = chain.request()
                    val modifiedRequest = originalRequest.newBuilder()
                        .header("X-ND-Authorization", "Bearer $authToken")
                        .build()
                    chain.proceed(modifiedRequest)
                }

                val logging =
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(authInterceptor)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("$serverUrl/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val apiService = retrofit.create(ApiService::class.java)

                val response = try {
                    withContext(Dispatchers.IO) {
                        apiService.getSongsForAlbum(album.id)
                    }
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Błąd podczas wywołania API: ${e.message}", e)
                    null
                }

                if (response != null && response.isSuccessful) {
                    val firstSong = response.body()!![0]
                    playedSongId = firstSong.id

                    streamUrl =
                        "$serverUrl/rest/stream?u=$username&t=$subsonicToken&s=$subsonicSalt&v=1.8.0&c=NavidromeUI&id=$playedSongId"

                    runOnUiThread {
                        // Możesz tutaj włączyć przyciski lub zaktualizować UI
                        playPauseButton.isEnabled = true
                    }
                } else {
                    Log.e("PlayerActivity", "Pusta lista piosenek lub null body")
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Error fetching song details for album: ${album.id}", e)
            }
        }

        runOnUiThread {
            playPauseButton.isEnabled = true // ← Włącz gdy dane są gotowe
        }
        val duration = intent.getLongExtra("DURATION", 0)

        playPauseButton.isEnabled = false

        album.let {
            albumNameTextView.text = it.name
            albumArtistTextView.text = getString(R.string.artist, it.albumArtist)
            albumCreatedTextView.text = getString(R.string.created, formatDate(it.createdAt))

            albumCoverImageView.load(it.coverArtUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder_album)
                error(R.drawable.ic_error_album)
                transformations(RoundedCornersTransformation(16f))
            }
        }

        totalDuration = duration
        totalTimeTextView.text = formatTime(totalDuration)

        playPauseButton.setOnClickListener {
            if (isPlaying.get()) {
                pausePlayback()
            } else {
                if (streamUrl != null) {
                    startPlayback(streamUrl)
                } else {
                    Log.e("PlayerActivity", "Stream URL nie jest jeszcze dostępny")
                }
            }
        }
        previousButton.setOnClickListener {
            skipBackward()
        }

        nextButton.setOnClickListener {
            skipForward()
        }

        // Obsługa interakcji z paskiem przewijania
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimeTextView.text = formatTime(progress.toLong()) // Aktualizuj czas
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Usuń callback podczas przewijania
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { bar ->
                    mediaPlayer?.seekTo(bar.progress) // Przewiń do wybranej pozycji
                    if (isPlaying.get()) {
                        handler.post(updateSeekBarRunnable) // Wznów aktualizację
                    }
                }
            }
        })
    }

    private fun startPlayback(url: String?) {
        if (url == null) return

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener {
                        it.start()
                        this@PlayerActivity.isPlaying.set(true)
                        playPauseButton.setImageResource(R.drawable.ic_pause)
                        seekBar.max = it.duration
                        totalDuration = it.duration.toLong()
                        totalTimeTextView.text = formatTime(totalDuration)

                        handler.post(updateSeekBarRunnable)
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("PlayerActivity", "Błąd MediaPlayer: what=$what, extra=$extra")
                        false
                    }
                    prepareAsync()
                }
            } else {
                mediaPlayer?.start()
                isPlaying.set(true)
                playPauseButton.setImageResource(R.drawable.ic_pause)
                handler.post(updateSeekBarRunnable)
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Błąd podczas rozpoczynania odtwarzania", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        isPlaying.set(false)
        playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    private fun formatTime(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatDate(dateString: String?): String {
        return try {
            if (dateString.isNullOrEmpty()) {
                return "Brak daty"
            }
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", Locale.getDefault())
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date: Date? = parser.parse(dateString)
            date?.let { formatter.format(it) } ?: "Brak daty"
        } catch (_: Exception) {
            "Błędna data"
        }
    }

    private fun skipBackward() {
        mediaPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val newPosition = max(0, currentPosition - SKIP_TIME_MS)
            player.seekTo(newPosition)
            seekBar.progress = newPosition
            currentTimeTextView.text = formatTime(newPosition.toLong())
            showSkipFeedback("-1:00")
        }
    }

    private fun skipForward() {
        mediaPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val newPosition =
                min(player.duration, currentPosition + SKIP_TIME_MS)
            player.seekTo(newPosition)
            seekBar.progress = newPosition
            currentTimeTextView.text = formatTime(newPosition.toLong())
            showSkipFeedback("+1:00")
        }
    }

    private fun showSkipFeedback(text: String) {
        Toast.makeText(this, "Przewinięto: $text", Toast.LENGTH_SHORT).show()
    }

}

