package com.example.mycarapp.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.mycarapp.R
import com.example.mycarapp.dto.Album
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var playPauseButton: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeTextView: TextView
    private lateinit var totalTimeTextView: TextView

    // Stan odtwarzacza
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            // Tutaj wstaw logikę pobierania aktualnej pozycji utworu z playera (np. MediaPlayer)
            val currentPosition = 0 // Symulacja aktualnej pozycji
            seekBar.progress = currentPosition
            currentTimeTextView.text = formatTime(currentPosition.toLong())

            // Jeśli odtwarzanie trwa, ponów zadanie
            if (isPlaying) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val album = intent.getParcelableExtra<Album>("ALBUM_DATA")

        val albumCoverImageView: ImageView = findViewById(R.id.player_album_cover_image_view)
        val albumNameTextView: TextView = findViewById(R.id.player_album_name_text_view)
        val albumArtistTextView: TextView = findViewById(R.id.player_album_artist_text_view)
        val albumCreatedTextView: TextView = findViewById(R.id.player_album_created_text_view)

        playPauseButton = findViewById(R.id.play_pause_button)
        seekBar = findViewById(R.id.player_seek_bar)
        currentTimeTextView = findViewById(R.id.current_time_text_view)
        totalTimeTextView = findViewById(R.id.total_time_text_view)
        val previousButton: ImageButton = findViewById(R.id.previous_button)
        val nextButton: ImageButton = findViewById(R.id.next_button)

        album?.let {
            albumNameTextView.text = it.name
            albumArtistTextView.text = "Artysta: ${it.albumArtist}"
            albumCreatedTextView.text = "Created: ${formatDate(it.createdAt)}"

            albumCoverImageView.load(it.coverArtUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder_album)
                error(R.drawable.ic_error_album)
                transformations(RoundedCornersTransformation(16f))
            }
        }

        // Symulacja ustawienia czasu trwania utworu
        val totalDuration = 180000 // 3 minuty w milisekundach
        totalTimeTextView.text = formatTime(totalDuration.toLong())
        seekBar.max = totalDuration

        // Obsługa kliknięć przycisków
        playPauseButton.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }
        previousButton.setOnClickListener {
            // Tutaj logika dla poprzedniego utworu
        }
        nextButton.setOnClickListener {
            // Tutaj logika dla następnego utworu
        }

        // Obsługa interakcji z paskiem przewijania
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Tutaj logika przewijania utworu do nowej pozycji
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ważne: usuń zadanie z Handlera, aby uniknąć wycieku pamięci
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    private fun startPlayback() {
        isPlaying = true
        playPauseButton.setImageResource(R.drawable.ic_pause)
        handler.post(updateSeekBarRunnable) // Rozpocznij aktualizację
        // Tutaj logika startu odtwarzania audio
    }

    private fun pausePlayback() {
        isPlaying = false
        playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        handler.removeCallbacks(updateSeekBarRunnable) // Zatrzymaj aktualizację
        // Tutaj logika pauzowania audio
    }

    private fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
        } catch (e: Exception) {
            "Błędna data"
        }
    }

}