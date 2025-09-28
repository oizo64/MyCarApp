package com.example.mycarapp.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.example.mycarapp.services.PlayerService
import com.example.mycarapp.utils.StreamUrlGenerator
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
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

    @Inject
    lateinit var streamUrlGenerator: StreamUrlGenerator

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
    private var exoPlayer: ExoPlayer? = null
    private var streamUrl: String? = null
    private val SKIP_TIME_MS = 60000

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        setupUI()
        initializeExoPlayer()

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

        // Uruchom serwis do odtwarzania w tle
        startPlayerService(album)
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
                    exoPlayer?.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Pause updates during seeking
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Resume updates after seeking
            }
        })

        playPauseButton.isEnabled = false
    }

    private fun initializeExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            runOnUiThread {
                                playPauseButton.setImageResource(R.drawable.ic_pause)
                                isPlayingSong.set(true)
                                seekBar.max = duration.toInt()
                                totalTimeTextView.text = formatTime(duration)
                                updateProgress()
                            }
                        }
                        Player.STATE_ENDED -> {
                            runOnUiThread {
                                playPauseButton.setImageResource(R.drawable.ic_play_arrow)
                                isPlayingSong.set(false)
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            // Buffering state
                        }
                        Player.STATE_IDLE -> {
                            // Idle state
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("PlayerActivity", "ExoPlayer error: ${error.message}", error)
                    runOnUiThread {
                        Toast.makeText(this@PlayerActivity, "Błąd odtwarzania: ${error.message}", Toast.LENGTH_SHORT).show()
                        playPauseButton.setImageResource(R.drawable.ic_play_arrow)
                        isPlayingSong.set(false)
                    }
                }
            })
        }
    }

    private fun updateProgress() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                val currentPosition = player.currentPosition
                seekBar.progress = currentPosition.toInt()
                currentTimeTextView.text = formatTime(currentPosition)
                // Update every second
                playPauseButton.postDelayed({ updateProgress() }, 1000)
            }
        }
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
                streamUrl = streamUrlGenerator.getFirstSongStreamUrlForAlbum(album.id)
                Log.d("PlayerActivity", "Generated stream URL: $streamUrl")

                runOnUiThread {
                    playPauseButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Błąd podczas pobierania URL strumienia: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Błąd pobierania strumienia: ${e.message}", Toast.LENGTH_SHORT).show()
                    playPauseButton.isEnabled = false
                }
            }
        }
    }

    private fun togglePlayback() {
        if (isPlayingSong.get()) {
            pausePlayback()
        } else {
            if (streamUrl == null) {
                Log.e("PlayerActivity", "Stream URL nie jest jeszcze dostępny")
                Toast.makeText(this, "Poczekaj, aż piosenka się załaduje.", Toast.LENGTH_SHORT).show()
                return
            }

            startPlayback()
        }
    }

    private fun startPlayback() {
        try {
            streamUrl?.let { url ->
                Log.d("PlayerActivity", "Starting playback with URL: ${url.take(100)}...")

                val mediaItem = MediaItem.fromUri(url)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
                exoPlayer?.play()
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Błąd podczas rozpoczynania odtwarzania", e)
            runOnUiThread {
                Toast.makeText(this, "Błąd odtwarzania: ${e.message}", Toast.LENGTH_SHORT).show()
                playPauseButton.setImageResource(R.drawable.ic_play_arrow)
                isPlayingSong.set(false)
            }
        }
    }

    private fun pausePlayback() {
        exoPlayer?.pause()
        isPlayingSong.set(false)
        runOnUiThread {
            playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun skipBackward() {
        exoPlayer?.let { player ->
            val newPosition = max(0, player.currentPosition - SKIP_TIME_MS)
            player.seekTo(newPosition)
            seekBar.progress = newPosition.toInt()
            currentTimeTextView.text = formatTime(newPosition)
            showSkipFeedback("-1:00")
        }
    }

    private fun skipForward() {
        exoPlayer?.let { player ->
            val newPosition = min(player.duration, player.currentPosition + SKIP_TIME_MS)
            player.seekTo(newPosition)
            seekBar.progress = newPosition.toInt()
            currentTimeTextView.text = formatTime(newPosition)
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

    private fun startPlayerService(album: Album) {
        val serviceIntent = Intent(this, PlayerService::class.java).apply {
            putExtra("ALBUM_DATA", album)
            putExtra("STREAM_URL", streamUrl)
        }
        startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }

    // USUNIĘTE: onPause() nie pauzuje już odtwarzania
    // override fun onPause() {
    //     super.onPause()
    //     pausePlayback()
    // }
}