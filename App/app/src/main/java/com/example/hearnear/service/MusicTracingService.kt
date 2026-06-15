package com.example.hearnear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.hearnear.R
import com.example.hearnear.viewmodel.ActivityViewModel
import com.example.hearnear.viewmodel.MusicData
import kotlinx.coroutines.*

class MusicTrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "MusicTrackingChannel"
        const val NOTIFICATION_ID = 1
        private var serviceInstance: MusicTrackingService? = null

        @RequiresApi(Build.VERSION_CODES.O)
        fun start(context: Context) {
            val intent = Intent(context, MusicTrackingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MusicTrackingService::class.java)
            context.stopService(intent)
        }

        fun updateMusic(musicData: MusicData) {
            serviceInstance?.updateMusicActivity(musicData)
        }
    }

    private var activityViewModel: ActivityViewModel? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedPrefs by lazy {
        getSharedPreferences("music_sharing_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel()
        activityViewModel = ActivityViewModel(applicationContext)
        Log.d("MusicService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d("MusicService", "Service started")
        return START_STICKY
    }

    fun updateMusicActivity(musicData: MusicData) {
        val visibility = getVisibility()
        if (visibility == "none") {
            Log.d("MusicService", "Visibility=none, skipping update")
            return
        }
        // Nadpisz visibility z prefs (MusicData może mieć stary stan)
        val dataWithVisibility = musicData.copy(visibility = visibility)
        serviceScope.launch {
            activityViewModel?.updateActivity(dataWithVisibility)
            Log.d("MusicService", "Updated activity – visibility=$visibility")
        }
    }

    /** Odczytuje aktualną widoczność z SharedPreferences. */
    private fun getVisibility(): String {
        return sharedPrefs.getString("music_visibility", "everyone") ?: "everyone"
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hear Near")
            .setContentText("Udostępnianie muzyki w tle")
            .setSmallIcon(R.drawable.outline_music_note_24)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps music tracking active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceInstance = null
        serviceScope.cancel()
        activityViewModel = null
        super.onDestroy()
        Log.d("MusicService", "Service destroyed")
    }
}