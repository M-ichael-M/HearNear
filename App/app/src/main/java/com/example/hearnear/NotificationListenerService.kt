package com.example.hearnear

import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.hearnear.service.MusicTrackingService
import com.example.hearnear.viewmodel.ActivityViewModel
import com.example.hearnear.viewmodel.MusicData

class MusicNotificationListener : NotificationListenerService() {

    private var activityViewModel: ActivityViewModel? = null
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL = 10000L
    private val sharedPrefs by lazy {
        getSharedPreferences("music_sharing_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        activityViewModel = ActivityViewModel(applicationContext)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg in listOf("com.spotify.music", "com.google.android.apps.youtube.music")) {
            val extras = sbn.notification.extras
            val artist = extras.getCharSequence("android.title")?.toString() ?: ""
            val track = extras.getCharSequence("android.text")?.toString() ?: ""

            if (track.isNotEmpty() && artist.isNotEmpty()) {
                val newMusicData = MusicData(
                    trackName = track,
                    artistName = artist,
                    albumName = null
                )

                // Zapisz nową muzykę do SharedPreferences
                saveMusicToPrefs(track, artist, null)
                Log.d("NowPlaying", "🎵 Sending: $track – $artist")

                if (isSharingEnabled()) {
                    // Upewnij się, że serwis działa
                    MusicTrackingService.start(applicationContext)
                    // Wyślij muzykę do serwera
                    MusicTrackingService.updateMusic(newMusicData)
                    Log.d("NowPlaying", "📍 Sent music to server")
                }
            }
        }
    }


    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg in listOf("com.spotify.music", "com.google.android.apps.youtube.music")) {
            Log.d("NowPlaying", "🛑 Music notification removed - music likely stopped")
            // Wyczyść zapisaną muzykę gdy powiadomienie zostanie usunięte
            clearMusicFromPrefs()
        }
    }

    private fun clearMusicFromPrefs() {
        sharedPrefs.edit()
            .remove("current_track")
            .remove("current_artist")
            .remove("current_album")
            .apply()
    }

    private fun isSharingEnabled(): Boolean {
        return sharedPrefs.getBoolean("music_sharing_enabled", false)
    }

    private fun saveMusicToPrefs(track: String, artist: String, album: String?) {
        sharedPrefs.edit()
            .putString("current_track", track)
            .putString("current_artist", artist)
            .putString("current_album", album)
            .apply()
    }

    override fun onListenerConnected() {
        Log.d("NowPlaying", "✅ Listener połączony!")
    }

    override fun onDestroy() {
        super.onDestroy()
        activityViewModel = null
    }
}