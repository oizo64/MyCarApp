package com.example.mycarapp.AndroidAutoTests

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.ConfigManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import dagger.hilt.android.AndroidEntryPoint
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem
import androidx.core.net.toUri
import androidx.core.content.edit

@AndroidEntryPoint
class MusicPlaybackService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var sessionCallback: MediaSessionCallback
    private val mediaItems = mutableListOf<MediaItem>()

    private var audioFocusRequest: AudioFocusRequest? = null

    private lateinit var appConfig: AppConfig

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "MusicServicePrefs"
        private const val KEY_LAST_MEDIA_ID = "last_media_id"
        const val STREAM_URL = "http://streams.90s90s.de/techno/mp3-192/"
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                exoPlayer.play()
            }

            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                sessionCallback.onPause()
            }
        }
    }

    private fun loadAlbumArtAsync(callback: (Bitmap?) -> Unit) {
        Thread {
            try {
                val url =
                    URL("https://i.pinimg.com/736x/1e/1e-fc/1e1efcc0e4005e2b93d321b9a69a6899.jpg")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doInput = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val input = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(input)
                    input.close()

                    Handler(Looper.getMainLooper()).post {
                        callback(bitmap)
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Error loading album art: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    callback(null)
                }
            }
        }.start()
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
                    exoPlayer.playbackState == ExoPlayer.STATE_READY -> PlaybackStateCompat.STATE_PAUSED
                    exoPlayer.playbackState == ExoPlayer.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                    exoPlayer.playbackState == ExoPlayer.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
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
        prepareLastPlayedOrDefault()
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
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(album.id)
                .setTitle(album.name)
                .setMediaUri(STREAM_URL.toUri())
                .build()

            mediaItems.add(MediaItem(description, FLAG_PLAYABLE))
        }

        if (mediaItems.isEmpty()) {
            val defaultDescription = MediaDescriptionCompat.Builder()
                .setMediaId("default_item")
                .setTitle("Brak albumów")
                .setSubtitle("Lista jest pusta")
                .setMediaUri(STREAM_URL.toUri())
                .build()
            mediaItems.add(MediaItem(defaultDescription, FLAG_PLAYABLE))
        }
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaItem>>
    ) {
        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        mediaSession.release()
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun setPlaybackState(@PlaybackStateCompat.State state: Int) {
        val actions = when (state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            }

            PlaybackStateCompat.STATE_STOPPED -> {
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            }

            else -> {
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            }
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, exoPlayer.currentPosition, 1.0f)
            .setActions(actions)
            .build()
        mediaSession.setPlaybackState(playbackState)

        Log.d("MusicService", "Playback state set to: $state")
    }

    private fun preparePlayer(mediaId: String) {
        val mediaUri = mediaItems.find { it.mediaId == mediaId }?.description?.mediaUri
        if (mediaUri != null) {
            val exoPlayerItem = ExoPlayerMediaItem.Builder()
                .setUri(mediaUri)
                .build()
            exoPlayer.setMediaItem(exoPlayerItem)
            exoPlayer.prepare()
            Log.d("MusicService", "Player prepared for mediaId: $mediaId")
        } else {
            Log.w("MusicService", "Could not find media item for mediaId: $mediaId")
        }
    }

    private fun prepareLastPlayedOrDefault() {
        val lastMediaId = sharedPreferences.getString(KEY_LAST_MEDIA_ID, null)
        if (lastMediaId != null) {
            preparePlayer(lastMediaId)
        } else if (mediaItems.isNotEmpty()) {
            val firstMediaId = mediaItems.first().mediaId!!
            preparePlayer(firstMediaId)
        } else {
            Log.w("MusicService", "Media item list is empty, cannot prepare player.")
        }
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            val result: Int
            result = audioFocusRequest?.let {
                audioManager.requestAudioFocus(it)
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                exoPlayer.play()
            }
        }

        override fun onPause() {
            exoPlayer.pause()
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }

        override fun onStop() {
            exoPlayer.stop()
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            mediaId ?: return

            val selectedMediaItem = mediaItems.find { it.mediaId == mediaId }
            selectedMediaItem?.let {
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        it.description.title.toString()
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        it.description.subtitle.toString()
                    ) // Przykładowo, jeśli masz artystę
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM,
                        it.description.title.toString()
                    ) // Album to nazwa utworu, zgodnie z prośbą
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                        it.description.mediaUri.toString()
                    )
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)

                mediaSession.setMetadata(metadataBuilder.build())
            }

            sharedPreferences.edit { putString(KEY_LAST_MEDIA_ID, mediaId) }
            Log.d("MusicService", "Saved last mediaId: $mediaId")

            preparePlayer(mediaId)
            onPlay()
        }
    }
}