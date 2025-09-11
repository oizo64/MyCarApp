package com.example.mycarapp.AndroidAutoTests

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
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
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem


@AndroidEntryPoint
class MusicPlaybackService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: Player
    private lateinit var audioManager: AudioManager
    private lateinit var sessionCallback: MediaSessionCallback
    private val mediaItems = mutableListOf<MediaItem>()

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
            private fun updatePlaybackState() {
                val state = when {
                    exoPlayer.isPlaying -> PlaybackStateCompat.STATE_PLAYING
                    exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.playWhenReady -> PlaybackStateCompat.STATE_PAUSED
                    exoPlayer.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                    exoPlayer.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
                    else -> PlaybackStateCompat.STATE_NONE
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

            mediaItems.add(MediaItem(description, FLAG_PLAYABLE))
        }

        if (mediaItems.isEmpty()) {
            val defaultDescription = MediaDescriptionCompat.Builder()
                .setMediaId("default_item")
                .setTitle("Brak albumów")
                .setSubtitle("Lista jest pusta")
                .setMediaUri(STREAM_URL.toUri())
                .setIconUri("https://i.pinimg.com/736x/1e/1e-fc/1e1efcc0e4005e2b93d321b9a69a6899.jpg".toUri())
                .build()
            mediaItems.add(MediaItem(defaultDescription, FLAG_PLAYABLE))
        }
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaItem>>
    ) {
        if (parentId == "root_id") {
            result.sendResult(mediaItems)
        } else {
            result.sendResult(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        exoPlayer.release()
        mediaSession.release()
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun setPlaybackState(@PlaybackStateCompat.State state: Int) {
        val currentPosition = exoPlayer.currentPosition
        val actions = getAvailableActions(state)

        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setState(state, currentPosition, 1.0f)
            .setActions(actions)

        if (state == PlaybackStateCompat.STATE_ERROR) {
            playbackStateBuilder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, "Wystąpił błąd odtwarzania")
        }

        mediaSession.setPlaybackState(playbackStateBuilder.build())
        Log.d("MusicService", "Playback state set to: $state at position $currentPosition")
    }

    private fun getAvailableActions(@PlaybackStateCompat.State state: Int): Long {
        var actions = (
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        // Zmieniamy znaczenie tych przycisków na przewijanie
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_STOP
                )
        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            // Dodajemy ACTION_SEEK_TO, jeśli odtwarzacz może przewijać
            if (exoPlayer.isCurrentMediaItemSeekable) {
                actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
            }
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
        val mediaItem = mediaItems.find { it.mediaId == mediaId }
        val mediaUri = mediaItem?.description?.mediaUri

        if (mediaUri != null) {
            val exoPlayerItem = ExoPlayerMediaItem.Builder()
                .setUri(mediaUri)
                .setMediaId(mediaId)
                .build()
            exoPlayer.setMediaItem(exoPlayerItem)
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.prepare()

            mediaItem.description.let { description ->
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, description.mediaId)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, description.title?.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, description.subtitle?.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, description.title?.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, description.mediaUri?.toString())
                    // Ustaw czas trwania, jeśli jest znany, w przeciwnym razie -1 dla strumieni
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (exoPlayer.duration > 0) exoPlayer.duration else -1L)
                description.iconUri?.let {
                    metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.toString())
                    metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, it.toString())
                }
                mediaSession.setMetadata(metadataBuilder.build())
            }

            Log.d("MusicService", "Player prepared for mediaId: $mediaId, playWhenReady: $playWhenReady")
        } else {
            Log.w("MusicService", "Could not find media item for mediaId: $mediaId")
            setPlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun prepareLastPlayedOrDefault(playWhenReady: Boolean) {
        val lastMediaId = sharedPreferences.getString(KEY_LAST_MEDIA_ID, null)
        val mediaIdToPrepare = if (lastMediaId != null && mediaItems.any { it.mediaId == lastMediaId }) {
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

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d("MusicService", "onPlay called")
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.currentMediaItem == null) {
                Log.d("MusicService", "Player not ready or no media item, preparing last played or default.")
                prepareLastPlayedOrDefault(playWhenReady = true)
            } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
                exoPlayer.playWhenReady = true
            } else {
                val result = audioFocusRequest?.let { audioManager.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    exoPlayer.playWhenReady = true
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
            val result = audioFocusRequest?.let { audioManager.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaSession.isActive = true
            } else {
                Log.w("MusicService", "Audio focus request failed for onPlayFromMediaId")
                exoPlayer.playWhenReady = false
            }
        }

        override fun onSeekTo(pos: Long) {
            if (exoPlayer.isCurrentMediaItemSeekable) {
                Log.d("MusicService", "onSeekTo called with position: $pos")
                exoPlayer.seekTo(pos)
                // Stan odtwarzania zostanie zaktualizowany przez listener ExoPlayera
                // Ale możemy go zaktualizować od razu, aby UI szybciej zareagowało
                setPlaybackState(mediaSession.controller.playbackState.state)
            } else {
                Log.w("MusicService", "Current media item is not seekable.")
            }
        }

        override fun onSkipToNext() {
            Log.d("MusicService", "onSkipToNext (seek forward) called")
            if (exoPlayer.isCurrentMediaItemSeekable) {
                var newPosition = exoPlayer.currentPosition + SEEK_INTERVAL_MS
                val duration = exoPlayer.duration
                if (duration > 0 && newPosition > duration) { // Upewnij się, że duration jest dostępne
                    newPosition = duration
                }
                exoPlayer.seekTo(newPosition)
                // Aktualizuj stan, aby odzwierciedlić nową pozycję
                setPlaybackState(mediaSession.controller.playbackState.state)
            } else {
                Log.w("MusicService", "Cannot skip next, media item not seekable or not playing.")
                // Opcjonalnie: przejdź do następnego utworu, jeśli przewijanie nie jest możliwe
                // super.onSkipToNext()
            }
        }

        override fun onSkipToPrevious() {
            Log.d("MusicService", "onSkipToPrevious (seek backward) called")
            if (exoPlayer.isCurrentMediaItemSeekable) {
                var newPosition = exoPlayer.currentPosition - SEEK_INTERVAL_MS
                if (newPosition < 0) {
                    newPosition = 0
                }
                exoPlayer.seekTo(newPosition)
                // Aktualizuj stan, aby odzwierciedlić nową pozycję
                setPlaybackState(mediaSession.controller.playbackState.state)
            } else {
                Log.w("MusicService", "Cannot skip previous, media item not seekable or not playing.")
                // Opcjonalnie: przejdź do poprzedniego utworu, jeśli przewijanie nie jest możliwe
                // super.onSkipToPrevious()
            }
        }
    }
}
