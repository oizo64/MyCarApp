package com.example.mycarapp.activities

import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.R
import com.example.mycarapp.dto.Album
import com.example.mycarapp.utils.StreamUrlGenerator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    private lateinit var albumCoverImageView: ImageView
    private lateinit var albumNameTextView: TextView
    private lateinit var albumArtistTextView: TextView
    private lateinit var albumCreatedTextView: TextView

    private lateinit var appConfig: AppConfig
    private var isPlayingSong = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var totalDuration: Long = 0
    private var streamUrl: String? = null
    private val SKIP_TIME_MS = 60000

    @Inject
    lateinit var streamUrlGenerator: StreamUrlGenerator // Hilt dostarczy zainicjalizowany generator

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                val currentPosition = player.currentPosition
                seekBar.progress = currentPosition
                currentTimeTextView.text = formatTime(currentPosition.toLong())
            }

            if (isPlayingSong.get()) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        setupUI()

        appConfig = configManager.getConfig()
        val album = intent.getParcelableExtra("ALBUM_DATA", Album::class.java)

        if (album == null) {
            Log.e("PlayerActivity", "Błąd: Brak danych albumu.")
            Toast.makeText(this, "Brak danych albumu.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        updateUIWithAlbumData(album)
        fetchAlbumDetails(album)
    }

    private fun setupUI() {
        playPauseButton = findViewById(R.id.play_pause_button)
        seekBar = findViewById(R.id.player_seek_bar)
        currentTimeTextView = findViewById(R.id.current_time_text_view)
        totalTimeTextView = findViewById(R.id.total_time_text_view)
        albumCoverImageView = findViewById(R.id.player_album_cover_image_view)
        albumNameTextView = findViewById(R.id.player_album_name_text_view)
        albumArtistTextView = findViewById(R.id.player_album_artist_text_view)
        albumCreatedTextView = findViewById(R.id.player_album_created_text_view)
        val previousButton: ImageButton = findViewById(R.id.previous_button)
        val nextButton: ImageButton = findViewById(R.id.next_button)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        playPauseButton.setOnClickListener {
            togglePlayback()
        }
        previousButton.setOnClickListener {
            skipBackward()
        }
        nextButton.setOnClickListener {
            skipForward()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimeTextView.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { bar ->
                    mediaPlayer?.seekTo(bar.progress)
                    if (isPlayingSong.get()) {
                        handler.post(updateSeekBarRunnable)
                    }
                }
            }
        })

        playPauseButton.isEnabled = false
    }

    private fun updateUIWithAlbumData(album: Album) {
        albumNameTextView.text = album.name
        albumArtistTextView.text = getString(R.string.artist, album.albumArtist)
        albumCreatedTextView.text = getString(R.string.created, formatDate(album.createdAt))

        albumCoverImageView.load(album.coverArtUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder_album)
            error(R.drawable.ic_error_album)
            transformations(RoundedCornersTransformation(16f))
        }
    }

    private fun fetchAlbumDetails(album: Album) {
        val serverUrl = appConfig.serverUrl
        val username = appConfig.username
        val subsonicToken = appConfig.subsonicToken
        val subsonicSalt = appConfig.subsonicSalt
        val authToken = appConfig.authToken

        if (serverUrl == null || username == null || subsonicToken == null || subsonicSalt == null || authToken == null) {
            Log.e("PlayerActivity", "Błąd: Brak wszystkich danych konfiguracyjnych.")
            Toast.makeText(this, "Błąd konfiguracji.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                streamUrl =
                    streamUrlGenerator.getFirstSongStreamUrlForAlbum(album.id);

                runOnUiThread {
                    playPauseButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(
                    "PlayerScreen",
                    "Błąd podczas pobierania szczegółów piosenki: ${e.message}",
                    e
                )
                Toast.makeText(this@PlayerActivity, "Błąd sieci: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }


    private fun togglePlayback() {
        if (isPlayingSong.get()) {
            pausePlayback()
        } else {
            streamUrl?.let { startPlayback(it) } ?: run {
                Log.e("PlayerActivity", "Stream URL nie jest jeszcze dostępny")
                Toast.makeText(this, "Poczekaj, aż piosenka się załaduje.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun startPlayback(url: String) {
        try {
            if (mediaPlayer == null) {
                initializeMediaPlayer(url)
            } else {
                mediaPlayer?.start()
                isPlayingSong.set(true)
                playPauseButton.setImageResource(R.drawable.ic_pause)
                handler.post(updateSeekBarRunnable)
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Błąd podczas rozpoczynania odtwarzania", e)
            Toast.makeText(this, "Błąd odtwarzania.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeMediaPlayer(url: String) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { player ->
                player.start()
                isPlayingSong.set(true)
                playPauseButton.setImageResource(R.drawable.ic_pause)
                seekBar.max = player.duration
                totalDuration = player.duration.toLong()
                totalTimeTextView.text = formatTime(totalDuration)
                handler.post(updateSeekBarRunnable)
            }
            setOnErrorListener { _, what, extra ->
                Log.e("PlayerActivity", "Błąd MediaPlayer: what=$what, extra=$extra")
                Toast.makeText(this@PlayerActivity, "Błąd odtwarzania.", Toast.LENGTH_SHORT).show()
                false
            }
            prepareAsync()
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        isPlayingSong.set(false)
        playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    private fun skipBackward() {
        mediaPlayer?.let { player ->
            val newPosition = max(0, player.currentPosition - SKIP_TIME_MS)
            player.seekTo(newPosition)
            seekBar.progress = newPosition
            currentTimeTextView.text = formatTime(newPosition.toLong())
            showSkipFeedback("-1:00")
        }
    }

    private fun skipForward() {
        mediaPlayer?.let { player ->
            val newPosition = min(player.duration, player.currentPosition + SKIP_TIME_MS)
            player.seekTo(newPosition)
            seekBar.progress = newPosition
            currentTimeTextView.text = formatTime(newPosition.toLong())
            showSkipFeedback("+1:00")
        }
    }

    private fun showSkipFeedback(text: String) {
        Toast.makeText(this, "Przewinięto: $text", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}