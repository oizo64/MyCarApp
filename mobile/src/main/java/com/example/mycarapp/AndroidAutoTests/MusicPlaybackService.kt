package com.example.mycarapp.AndroidAutoTests

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.utils.StreamUrlGenerator
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.support.v4.media.MediaBrowserCompat.MediaItem as BrowserMediaItem
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem

@AndroidEntryPoint
class MusicPlaybackService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var sessionCallback: MediaSessionCallback
    private val mediaItems = mutableListOf<BrowserMediaItem>()

    private var audioFocusRequest: AudioFocusRequest? = null

    private lateinit var appConfig: AppConfig

    private lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var streamUrlGenerator: StreamUrlGenerator

    companion object {
        private const val PREFS_NAME = "MusicServicePrefs"
        private const val KEY_LAST_MEDIA_ID = "last_media_id"
        const val STREAM_URL = "http://streams.90s90s.de/techno/mp3-192/"
        private val SEEK_INTERVAL_MS = TimeUnit.SECONDS.toMillis(60) // 60 sekund
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (mediaSession.controller.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                    exoPlayer.play()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                sessionCallback.onPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (exoPlayer.isPlaying) {
                    sessionCallback.onPause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Można obsłużyć ducking
            }
        }
    }

    private val positionUpdateHandler = Handler(Looper.getMainLooper())
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                if ((exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY) &&
                    exoPlayer.playbackState != Player.STATE_IDLE &&
                    exoPlayer.playbackState != Player.STATE_ENDED
                ) {

                    val currentPosition = exoPlayer.currentPosition
                    val duration =
                        if (exoPlayer.duration > 0 && exoPlayer.duration != Long.MAX_VALUE)
                            exoPlayer.duration else 0L

                    // Aktualizuj stan z aktualną pozycją i czasem trwania
                    val playbackState = PlaybackStateCompat.Builder()
                        .setState(
                            if (exoPlayer.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                            currentPosition,
                            1.0f
                        )
                        .setActions(
                            getAvailableActions(
                                if (exoPlayer.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                            )
                        )
                        .setBufferedPosition(exoPlayer.bufferedPosition)
                        .setExtras(Bundle().apply {
                            putLong("current_position", currentPosition)
                            putLong("duration", duration)
                            putBoolean("is_stream", duration <= 0)
                        })
                        .build()

                    mediaSession.setPlaybackState(playbackState)

                    // Aktualizuj również metadane z czasem trwania (dla interfejsu)
                    updateMetadataWithDuration(duration)
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Position update error: ${e.message}")
            } finally {
                positionUpdateHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appConfig = configManager.getConfig()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        exoPlayer = ExoPlayer.Builder(this).build()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setAudioAttributes(audioAttributes)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            private var lastDuration: Long = -1L

            private fun updatePlaybackState() {
                val state = when {
                    exoPlayer.isPlaying -> PlaybackStateCompat.STATE_PLAYING
                    exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.playWhenReady -> PlaybackStateCompat.STATE_PAUSED
                    exoPlayer.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                    exoPlayer.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
                    else -> PlaybackStateCompat.STATE_NONE
                }

                // Aktualizuj czas trwania gdy player jest ready
                if (exoPlayer.playbackState == Player.STATE_READY) {
                    val duration =
                        if (exoPlayer.duration > 0 && exoPlayer.duration != Long.MAX_VALUE)
                            exoPlayer.duration else 0L

                    // Aktualizuj tylko jeśli czas trwania się zmienił
                    if (duration != lastDuration) {
                        lastDuration = duration
                        // Znajdź aktualny media item i zaktualizuj metadane
                        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                        val mediaBrowserItem =
                            currentMediaId?.let { id -> mediaItems.find { it.mediaId == id } }
                        mediaBrowserItem?.description?.let {
                            updateMediaMetadataWithDuration(it, duration)
                        }
                        Log.d("MusicService", "Duration updated: ${formatTime(duration)}")
                    }
                }

                setPlaybackState(state)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicService", "ExoPlayer Error: ", error)
                setPlaybackState(PlaybackStateCompat.STATE_ERROR)
            }
        })

        sessionCallback = MediaSessionCallback()

        mediaSession = MediaSessionCompat(baseContext, "MusicPlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(sessionCallback)
            setSessionToken(sessionToken)
        }

        createMediaItems()
        prepareLastPlayedOrDefault(playWhenReady = false)
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)

        // Uruchom okresowe aktualizacje pozycji
        positionUpdateHandler.post(positionUpdateRunnable)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        mediaSession.isActive = true
        return BrowserRoot("root_id", null)
    }

    private fun createMediaItems() {
        val sortedAlbums = appConfig.sortedAlbums
        mediaItems.clear()

        sortedAlbums.forEach { album ->
            val streamUrlForAlbum =
                runBlocking {
                    streamUrlGenerator.getFirstSongStreamUrlForAlbum(album.id)
                }

            val description = MediaDescriptionCompat.Builder()
                .setMediaId(album.id)
                .setTitle(album.name)
                .setMediaUri(streamUrlForAlbum?.toUri())
                .setIconUri(album.coverArtUrl?.toUri())
                .build()

            mediaItems.add(BrowserMediaItem(description, FLAG_PLAYABLE))
        }

        if (mediaItems.isEmpty()) {
            val defaultDescription = MediaDescriptionCompat.Builder()
                .setMediaId("default_item")
                .setTitle("Brak albumów")
                .setSubtitle("Lista jest pusta")
                .setMediaUri(STREAM_URL.toUri())
                .setIconUri("https://i.pinimg.com/736x/1e/1e-fc/1e1efcc0e4005e2b93d321b9a69a6899.jpg".toUri())
                .build()
            mediaItems.add(BrowserMediaItem(defaultDescription, FLAG_PLAYABLE))
        }
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<BrowserMediaItem>>
    ) {
        if (parentId == "root_id") {
            result.sendResult(mediaItems)
        } else {
            result.sendResult(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        positionUpdateHandler.removeCallbacks(positionUpdateRunnable)
        mediaSession.isActive = false
        exoPlayer.release()
        mediaSession.release()
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun setPlaybackState(@PlaybackStateCompat.State state: Int) {
        val currentPosition = exoPlayer.currentPosition
        val duration =
            if (exoPlayer.duration > 0 && exoPlayer.duration != Long.MAX_VALUE) exoPlayer.duration else 0L
        val isStream = exoPlayer.duration <= 0

        val actions = getAvailableActions(state)

        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setState(state, currentPosition, 1.0f)
            .setActions(actions)
            .setBufferedPosition(exoPlayer.bufferedPosition)

        // Zawsze ustawiaj extras z aktualną pozycją i czasem trwania
        playbackStateBuilder.setExtras(Bundle().apply {
            putLong("current_position", currentPosition)
            putLong("duration", duration)
            putBoolean("is_stream", isStream)
        })

        if (state == PlaybackStateCompat.STATE_ERROR) {
            playbackStateBuilder.setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
                "Wystąpił błąd odtwarzania"
            )
        }

        mediaSession.setPlaybackState(playbackStateBuilder.build())
        Log.d(
            "MusicService",
            "Playback state set to: $state at position ${formatTime(currentPosition)}/${
                formatTime(duration)
            }"
        )
    }

    private fun getAvailableActions(@PlaybackStateCompat.State state: Int): Long {
        val isStream = exoPlayer.duration <= 0

        var actions = (
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_STOP
                )

        // Dodajemy ACTION_SEEK_TO tylko dla plików (nie dla strumieni)
        if (!isStream) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        }

        actions = when (state) {
            PlaybackStateCompat.STATE_PLAYING -> actions or PlaybackStateCompat.ACTION_PAUSE
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_STOPPED,
            PlaybackStateCompat.STATE_NONE -> actions or PlaybackStateCompat.ACTION_PLAY

            else -> actions
        }
        return actions
    }

    private fun preparePlayer(mediaId: String, playWhenReady: Boolean) {
        val mediaBrowserItem = mediaItems.find { it.mediaId == mediaId }
        val mediaUri = mediaBrowserItem?.description?.mediaUri

        if (mediaUri != null) {
            // Najpierw zatrzymaj i wyczyść istniejące media
            if (exoPlayer.playbackState != Player.STATE_IDLE) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }

            val exoPlayerItem = ExoPlayerMediaItem.Builder()
                .setUri(mediaUri)
                .setMediaId(mediaId)
                .build()
            exoPlayer.setMediaItem(exoPlayerItem)
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.prepare()

            // Ustaw początkowe metadane
            val initialDuration =
                if (exoPlayer.duration > 0 && exoPlayer.duration != Long.MAX_VALUE)
                    exoPlayer.duration else 0L
            updateMediaMetadataWithDuration(mediaBrowserItem.description, initialDuration)

            Log.d(
                "MusicService",
                "Player prepared for mediaId: $mediaId, playWhenReady: $playWhenReady, initial duration: ${
                    formatTime(initialDuration)
                }"
            )
        } else {
            Log.w("MusicService", "Could not find media item for mediaId: $mediaId")
            setPlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun prepareLastPlayedOrDefault(playWhenReady: Boolean) {
        val lastMediaId = sharedPreferences.getString(KEY_LAST_MEDIA_ID, null)
        val mediaIdToPrepare =
            if (lastMediaId != null && mediaItems.any { it.mediaId == lastMediaId }) {
                lastMediaId
            } else if (mediaItems.isNotEmpty()) {
                mediaItems.first().mediaId
            } else {
                null
            }

        if (mediaIdToPrepare != null) {
            preparePlayer(mediaIdToPrepare, playWhenReady)
        } else {
            Log.w("MusicService", "Media item list is empty, cannot prepare player.")
            setPlaybackState(PlaybackStateCompat.STATE_NONE)
        }
    }

    private fun updateMediaMetadataWithDuration(
        description: MediaDescriptionCompat?,
        duration: Long
    ) {
        description?.let { desc ->
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, desc.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, desc.title?.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, desc.subtitle?.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, desc.title?.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, desc.mediaUri?.toString())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

            desc.iconUri?.let { iconUri ->
                metadataBuilder.putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                    iconUri.toString()
                )
                metadataBuilder.putString(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                    iconUri.toString()
                )
            }

            mediaSession.setMetadata(metadataBuilder.build())
            Log.d("MusicService", "Metadata updated with duration: ${formatTime(duration)}")
        }
    }

    private fun updateMetadataWithDuration(duration: Long) {
        val currentMetadata = mediaSession.controller.metadata
        if (currentMetadata != null) {
            val metadataBuilder = MediaMetadataCompat.Builder(currentMetadata)
            if (duration > 0) {
                metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            }
            mediaSession.setMetadata(metadataBuilder.build())
        }
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "--:--"
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun performSeek(pos: Long) {
        val wasPlaying = exoPlayer.isPlaying

        // Tymczasowo zatrzymaj aktualizacje pozycji
        positionUpdateHandler.removeCallbacks(positionUpdateRunnable)

        exoPlayer.seekTo(pos)

        // Natychmiastowa aktualizacja stanu z nową pozycją
        val currentState =
            if (wasPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        setPlaybackState(currentState)

        // Wznów odtwarzanie jeśli było włączone
        if (wasPlaying) {
            exoPlayer.play()
        }

        // Wznów aktualizacje pozycji
        positionUpdateHandler.post(positionUpdateRunnable)
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d("MusicService", "onPlay called")
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED || exoPlayer.currentMediaItem == null) {
                Log.d(
                    "MusicService",
                    "Player not ready or no media item, preparing last played or default."
                )
                prepareLastPlayedOrDefault(playWhenReady = true)
            } else if (exoPlayer.playbackState == Player.STATE_READY) {
                val result = audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
                    ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    exoPlayer.play()
                    mediaSession.isActive = true
                } else {
                    Log.w("MusicService", "Audio focus request failed")
                }
            }
        }

        override fun onPause() {
            Log.d("MusicService", "onPause called")
            exoPlayer.playWhenReady = false
        }

        override fun onStop() {
            Log.d("MusicService", "onStop called")
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            mediaSession.isActive = false
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            mediaId ?: return
            Log.d("MusicService", "onPlayFromMediaId called with mediaId: $mediaId")

            sharedPreferences.edit { putString(KEY_LAST_MEDIA_ID, mediaId) }
            Log.d("MusicService", "Saved last mediaId: $mediaId")

            preparePlayer(mediaId, playWhenReady = true)
            val result = audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
                ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaSession.isActive = true
            } else {
                Log.w("MusicService", "Audio focus request failed for onPlayFromMediaId")
                exoPlayer.playWhenReady = false
            }
        }

        override fun onSeekTo(pos: Long) {
            val isStream = exoPlayer.duration <= 0
            if (isStream) {
                Log.w("MusicService", "Cannot seek in stream")
                return
            }

            Log.d("MusicService", "onSeekTo called with position: $pos")

            try {
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                    Log.w("MusicService", "Player not ready for seeking, preparing first")
                    prepareLastPlayedOrDefault(playWhenReady = true)
                    // Poczekaj chwilę aż player będzie gotowy
                    Handler(Looper.getMainLooper()).postDelayed({
                        performSeek(pos)
                    }, 500)
                } else {
                    performSeek(pos)
                }

            } catch (e: Exception) {
                Log.e("MusicService", "Seek error: ${e.message}")
            }
        }

        override fun onSkipToNext() {
            val duration =
                if (exoPlayer.duration > 0 && exoPlayer.duration != Long.MAX_VALUE) exoPlayer.duration else 0L
            val isStream = duration <= 0

            if (isStream) {
                Log.w("MusicService", "Cannot skip in stream")
                return
            }

            Log.d(
                "MusicService",
                "onSkipToNext (seek forward) called, duration: ${formatTime(duration)}"
            )

            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                prepareLastPlayedOrDefault(playWhenReady = true)
                return
            }

            if (exoPlayer.isCurrentMediaItemSeekable) {
                var newPosition = exoPlayer.currentPosition + SEEK_INTERVAL_MS
                if (newPosition > duration) {
                    newPosition = duration - 1000 // Zostaw 1 sekundę marginesu
                }
                performSeek(newPosition)
                Log.d(
                    "MusicService",
                    "Skipped to: ${formatTime(newPosition)}/${formatTime(duration)}"
                )
            } else {
                Log.w("MusicService", "Cannot skip next, media item not seekable or no duration")
            }
        }

        override fun onSkipToPrevious() {
            val duration =
                if (exoPlayer.duration > 0 && exoPlayer.duration != Long.MAX_VALUE) exoPlayer.duration else 0L
            val isStream = duration <= 0

            if (isStream) {
                Log.w("MusicService", "Cannot skip in stream")
                return
            }

            Log.d(
                "MusicService",
                "onSkipToPrevious (seek backward) called, duration: ${formatTime(duration)}"
            )

            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                prepareLastPlayedOrDefault(playWhenReady = true)
                return
            }

            if (exoPlayer.isCurrentMediaItemSeekable && duration > 0) {
                var newPosition = exoPlayer.currentPosition - SEEK_INTERVAL_MS
                if (newPosition < 0) {
                    newPosition = 0
                }
                performSeek(newPosition)
                Log.d(
                    "MusicService",
                    "Skipped to: ${formatTime(newPosition)}/${formatTime(duration)}"
                )
            } else {
                Log.w(
                    "MusicService",
                    "Cannot skip previous, media item not seekable or no duration"
                )
            }
        }
    }
}