package com.example.hearnear

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MusicNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg in listOf("com.spotify.music", "com.google.android.apps.youtube.music")) {
            val extras = sbn.notification.extras
            val artist = extras.getCharSequence("android.title")?.toString() ?: ""
            val track  = extras.getCharSequence("android.text")?.toString()  ?: ""

            Log.d("NowPlaying", "🎵 $track – $artist")

            // Wyślij broadcast do aplikacji
            val intent = Intent().apply {
                action = "com.example.hearnear.NOW_PLAYING"
                putExtra("track", track)
                putExtra("artist", artist)
            }
            sendBroadcast(intent)
        }
    }
    override fun onListenerConnected() {
        Log.d("NowPlaying", "✅ Listener połączony!")
    }

}
