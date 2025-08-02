package com.example.hearnear

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hearnear.ui.HearNearApp
import com.example.hearnear.ui.HearNearScreen
import com.example.hearnear.ui.theme.HearNearTheme
import com.example.hearnear.viewmodel.AuthViewModel
import com.google.android.gms.location.LocationServices
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

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

        setContent {
            MaterialTheme {
                val authViewModel: AuthViewModel = viewModel {
                    AuthViewModel(applicationContext)
                }
                val authState by authViewModel.authState.collectAsState()

                // Sprawdź i uruchom NotificationListener gdy użytkownik jest zalogowany
                LaunchedEffect(authState.isLoggedIn) {
                    if (authState.isLoggedIn) {
                        checkAndEnableNotificationListener()
                    }
                }

                if (authState.isLoggedIn) {
                    HearNearApp(authViewModel = authViewModel)
                } else {
                    AuthApp(authViewModel = authViewModel)
                }
            }
        }
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