package com.example.mycarapp.AndroidAutoTests

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem


class MusicPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var sessionCallback: MediaSessionCallback
    private val mediaItems = mutableListOf<MediaItem>()

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
        // Stwórz metadane na podstawie informacji o utworze
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "90s90s Techno")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "90s90s")
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, "http://streams.90s90s.de/techno/mp3-192/")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L) // Dodanie długości, -1 dla strumieni

        // Możesz dodać więcej pól, jeśli chcesz, np. album, grafika itp.

        mediaSession.setMetadata(metadataBuilder.build())
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
                        if (exoPlayer.playWhenReady) {
                            setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        } else {
                            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                        }
                    }
                    ExoPlayer.STATE_ENDED -> {
                        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
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
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, exoPlayer.currentPosition, 1.0f)
            .setActions(
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_PAUSE
                } else {
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE
                }
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Usuwamy setPlaybackState(), bo zrobi to słuchacz
                exoPlayer.play()
            }
        }

        override fun onPause() {
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            // Usuwamy setPlaybackState(), bo zrobi to słuchacz
            exoPlayer.pause()
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