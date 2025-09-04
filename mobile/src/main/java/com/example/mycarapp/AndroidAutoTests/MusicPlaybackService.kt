package com.example.mycarapp.AndroidAutoTests

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
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
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem


class MusicPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var sessionCallback: MediaSessionCallback
    private val mediaItems = mutableListOf<MediaItem>()
    private val streamUrl = "http://streams.90s90s.de/techno/mp3-192/"


    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                sessionCallback.onPlay()
            }

            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                sessionCallback.onPause()
            }
        }
    }

    private fun updateMediaSessionMetadata() {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "90s90s Techno")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "90s90s")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "90s90s Radio")
            .putString(
                MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                "http://streams.90s90s.de/techno/mp3-192/"
            )
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)

        // Ustaw tymczasowe metadane bez obrazka
        mediaSession.setMetadata(metadataBuilder.build())

        // Pobierz obrazek w tle i zaktualizuj metadane
        loadAlbumArtAsync { bitmap ->
            bitmap?.let {
                val updatedMetadata = MediaMetadataCompat.Builder(mediaSession.controller.metadata)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    .build()
                mediaSession.setMetadata(updatedMetadata)
            }
        }
    }

    private fun loadAlbumArtAsync(callback: (Bitmap?) -> Unit) {
        Thread {
            try {
                val url =
                    URL("https://i.pinimg.com/736x/1e/1e/fc/1e1efcc0e4005e2b93d321b9a69a6899.jpg")
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

        // 1. Inicjalizacja ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.playWhenReady = false

        // KLUCZOWA ZMIANA: Dodanie słuchacza ExoPlayer.Listener, który synchronizuje stan
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    ExoPlayer.STATE_BUFFERING -> {
                        setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                    }

                    ExoPlayer.STATE_READY -> {
                        if (exoPlayer.isPlaying) {
                            setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        } else {
                            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                        }
                    }

                    ExoPlayer.STATE_ENDED -> {
                        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    }

                    ExoPlayer.STATE_IDLE -> {
                        setPlaybackState(PlaybackStateCompat.STATE_NONE)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Dodatkowe zabezpieczenie na zmianę stanu odtwarzania
                if (exoPlayer.playbackState == ExoPlayer.STATE_READY) {
                    if (isPlaying) {
                        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    } else {
                        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        })

        sessionCallback = MediaSessionCallback()

        // 2. Inicjalizacja MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, "MusicPlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(sessionCallback)
            setSessionToken(sessionToken)
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 3. Ustawienie POCZĄTKOWEGO STANU na PAUSED
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)

        createMediaItems()
        updateMediaSessionMetadata()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        // KLUCZOWY KROK: Aktywacja sesji multimediów.
        mediaSession.isActive = true
        return BrowserRoot("root_id", null)
    }

    private fun createMediaItems() {
        val song1 = MediaDescriptionCompat.Builder()
            .setMediaId("song_1")
            .setTitle("90s90s Techno")
            .setSubtitle("90s90s")
            .setMediaUri(Uri.parse("http://streams.90s90s.de/techno/mp3-192/"))
            .build()

        mediaItems.clear()
        mediaItems.add(MediaItem(song1, FLAG_PLAYABLE))
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
        audioManager.abandonAudioFocus(audioFocusChangeListener)
    }

    private fun setPlaybackState(@PlaybackStateCompat.State state: Int) {
        val actions = when (state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                // GRA - pokaż PAUSE, STOP, SKIP
                PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                // PAUZA - pokaż PLAY, STOP, SKIP
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            }

            PlaybackStateCompat.STATE_STOPPED -> {
                // STOP - pokaż PLAY, SKIP
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            }

            else -> {
                // Dla innych stanów
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

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                exoPlayer.play()
                // Stan zostanie zaktualizowany przez listenera ExoPlayera
            }
        }

        override fun onPause() {
            exoPlayer.pause()
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            // Stan zostanie zaktualizowany przez listenera ExoPlayera
        }

        override fun onStop() {
            exoPlayer.stop()
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val mediaUri = mediaItems.find { it.mediaId == mediaId }?.description?.mediaUri
            if (mediaUri != null) {
                val exoPlayerItem = ExoPlayerMediaItem.Builder()
                    .setUri(mediaUri)
                    .build()
                exoPlayer.setMediaItem(exoPlayerItem)
                exoPlayer.prepare()
                onPlay()
            }
        }
    }
}