package com.example.mycarapp.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mycarapp.R
import com.example.mycarapp.dto.Album
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager

class PlayerService : Service() {
    private var exoPlayer: ExoPlayer? = null
    private lateinit var notificationManager: PlayerNotificationManager

    override fun onCreate() {
        super.onCreate()
        initializeExoPlayer()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getParcelableExtra<Album>("ALBUM_DATA")?.let { album ->
            val streamUrl = intent.getStringExtra("STREAM_URL")
            streamUrl?.let { url ->
                startPlayback(url, album)
            }
        }
        return START_STICKY
    }

    private fun initializeExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()

        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            "music_channel"
        ).build().apply {
            setPlayer(exoPlayer)
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startPlayback(streamUrl: String, album: Album) {
        try {
            val mediaItem = MediaItem.fromUri(streamUrl)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()

            // Ustaw powiadomienie jako foreground service
            startForeground(NOTIFICATION_ID, createNotification(album))
        } catch (e: Exception) {
            Log.e("PlayerService", "Błąd odtwarzania", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "music_channel",
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            // POPRAWKA: Użyj applicationContext zamiast this
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(album: Album): Notification {
        return NotificationCompat.Builder(this, "music_channel")
            .setContentTitle(album.name)
            .setContentText(album.albumArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        stopForeground(true)
        stopSelf()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}