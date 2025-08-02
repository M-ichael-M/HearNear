package com.example.hearnear.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@Composable
fun NotificationPermissionChecker() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf(listOf<String>()) }

    // Lista uprawnień do sprawdzenia
    val permissionsToCheck = listOf(
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Launcher do obsługi wyniku z ustawień
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Po powrocie z ustawień sprawdzamy uprawnienia
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        isNotificationEnabled = enabledListeners?.contains(context.packageName) == true
    }

    // Launcher do żądania uprawnień systemowych
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Po nadaniu uprawnień aktualizujemy listę brakujących
        missingPermissions = permissionsToCheck.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    // Funkcja sprawdzająca wszystkie uprawnienia
    fun checkAllPermissions() {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        isNotificationEnabled = enabledListeners?.contains(context.packageName) == true

        missingPermissions = permissionsToCheck.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        showDialog = !isNotificationEnabled || missingPermissions.isNotEmpty()
    }

    // Sprawdzanie uprawnień przy każdym uruchomieniu i po zmianach
    LaunchedEffect(Unit) {
        while (true) {
            checkAllPermissions()
            kotlinx.coroutines.delay(1000) // Odświeżanie co 1 sekundę
        }
    }

    if (!isNotificationEnabled || missingPermissions.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎵 Wymagane uprawnienia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = buildString {
                        append("Aplikacja potrzebuje następujących uprawnień:\n")
                        if (!isNotificationEnabled) {
                            append("- Dostęp do powiadomień\n")
                        }
                        if (missingPermissions.contains(Manifest.permission.FOREGROUND_SERVICE)) {
                            append("- Usługa pierwszoplanowa\n")
                        }
                        if (missingPermissions.contains(Manifest.permission.INTERNET)) {
                            append("- Dostęp do internetu\n")
                        }
                        if (missingPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
                            missingPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            append("- Lokalizacja\n")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (!isNotificationEnabled) {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            notificationSettingsLauncher.launch(intent)
                        }
                        if (missingPermissions.isNotEmpty()) {
                            permissionLauncher.launch(permissionsToCheck.toTypedArray())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Włącz uprawnienia")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("Wymagane uprawnienia")
            },
            text = {
                Text(
                    buildString {
                        append("HearNear potrzebuje następujących uprawnień:\n")
                        if (!isNotificationEnabled) {
                            append("- Dostęp do powiadomień do wyświetlania muzyki\n")
                        }
                        if (missingPermissions.contains(Manifest.permission.FOREGROUND_SERVICE)) {
                            append("- Usługa pierwszoplanowa do działania w tle\n")
                        }
                        if (missingPermissions.contains(Manifest.permission.INTERNET)) {
                            append("- Internet do łączności sieciowej\n")
                        }
                        if (missingPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
                            missingPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            append("- Lokalizacja do funkcji opartych na położeniu\n")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        if (!isNotificationEnabled) {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            notificationSettingsLauncher.launch(intent)
                        }
                        if (missingPermissions.isNotEmpty()) {
                            permissionLauncher.launch(permissionsToCheck.toTypedArray())
                        }
                    }
                ) {
                    Text("Przejdź do ustawień")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Anuluj")
                }
            }
        )
    }
}