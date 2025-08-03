package com.example.hearnear

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hearnear.ui.HearNearApp
import com.example.hearnear.viewmodel.AuthViewModel
import com.example.hearnear.viewmodel.NearbyListenersViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicjalizacja MapLibre z kluczem API
        MapLibre.getInstance(
            applicationContext,
            "zJQ3iHNAru3LOgSIwA5p",
            WellKnownTileServer.MapTiler
        )
        hideSystemUI()
        setContent {
            MaterialTheme {
                val authViewModel: AuthViewModel = viewModel {
                    AuthViewModel(applicationContext)
                }
                val nearbyListenersViewModel: NearbyListenersViewModel = viewModel {
                    NearbyListenersViewModel(applicationContext)
                }
                val authState by authViewModel.authState.collectAsState()

                // Sprawdź i uruchom NotificationListener gdy użytkownik jest zalogowany
                LaunchedEffect(authState.isLoggedIn) {
                    if (authState.isLoggedIn) {
                        checkAndEnableNotificationListener()
                    }
                }

                if (authState.isLoggedIn) {
                    HearNearApp(
                        authViewModel = authViewModel,
                        nearbyListenersViewModel = nearbyListenersViewModel
                    )
                } else {
                    AuthApp(authViewModel = authViewModel)
                }
            }
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }


    private fun checkAndEnableNotificationListener() {
        if (!isNotificationServiceEnabled()) {
            // Jeśli NotificationListener nie jest włączony, otwórz ustawienia
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = packageName
        return enabledListeners?.contains(packageName) == true
    }
}