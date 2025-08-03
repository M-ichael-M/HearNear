package com.example.hearnear

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.hearnear.viewmodel.ActivityViewModel
import com.example.hearnear.viewmodel.MusicData

class MusicNotificationListener : NotificationListenerService() {

    private var activityViewModel: ActivityViewModel? = null
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL = 10000L // 10 sekund między aktualizacjami

    override fun onCreate() {
        super.onCreate()
        activityViewModel = ActivityViewModel(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg in listOf("com.spotify.music", "com.google.android.apps.youtube.music")) {
            val extras = sbn.notification.extras
            val artist = extras.getCharSequence("android.title")?.toString() ?: ""
            val track = extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d("NowPlaying", "🎵 $track – $artist")

            // Sprawdź czy minął odpowiedni czas od ostatniej aktualizacji
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {

                if (track.isNotEmpty() && artist.isNotEmpty()) {
                    val musicData = MusicData(
                        trackName = track,
                        artistName = artist,
                        albumName = null // Możesz dodać logikę do pobierania albumu jeśli dostępne
                    )

                    activityViewModel?.updateActivity(musicData)
                    lastUpdateTime = currentTime

                    Log.d("NowPlaying", "📍 Sending activity to server: $track by $artist")
                }
            }
        }
    }

    override fun onListenerConnected() {
        Log.d("NowPlaying", "✅ Listener połączony!")
    }

    override fun onDestroy() {
        super.onDestroy()
        activityViewModel = null
    }
}