package com.example.hearnear.ui.screens

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hearnear.ui.components.NotificationPermissionChecker
import com.example.hearnear.viewmodel.NearbyListenersViewModel
import com.example.hearnear.R
import com.example.hearnear.network.NearbyListener
import com.example.hearnear.service.MusicTrackingService
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(nearbyListenersViewModel: NearbyListenersViewModel) {
    val state by nearbyListenersViewModel.state.collectAsState()
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("music_sharing_prefs", Context.MODE_PRIVATE)
    }

    var isSharingEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("music_sharing_enabled", false))
    }

    // Aktualnie odtwarzana muzyka – odświeżana co 2 sekundy
    var currentTrack by remember { mutableStateOf<String?>(null) }
    var currentArtist by remember { mutableStateOf<String?>(null) }
    var currentAlbum by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTrack = sharedPrefs.getString("current_track", null)
            currentArtist = sharedPrefs.getString("current_artist", null)
            currentAlbum = sharedPrefs.getString("current_album", null)
            delay(2000)
        }
    }

    LaunchedEffect(Unit) {
        nearbyListenersViewModel.loadNearbyListeners()
        nearbyListenersViewModel.startAutoRefresh()
    }

    NotificationPermissionChecker()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Hear Near",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // --- Karta: Co teraz grasz ---
        NowPlayingCard(
            track = currentTrack,
            artist = currentArtist,
            album = currentAlbum,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Przełącznik udostępniania muzyki
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSharingEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Udostępniaj muzykę",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSharingEnabled) "Twoja muzyka jest udostępniana"
                        else "Włącz aby inni mogli Cię znaleźć",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isSharingEnabled,
                    onCheckedChange = { enabled ->
                        isSharingEnabled = enabled
                        sharedPrefs.edit()
                            .putBoolean("music_sharing_enabled", enabled)
                            .apply()

                        if (enabled) {
                            MusicTrackingService.start(context)
                        } else {
                            MusicTrackingService.stop(context)
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Słuchacze w pobliżu (${state.listeners.size})",
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(
                onClick = { nearbyListenersViewModel.refreshListeners() },
                enabled = !state.isLoading
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_refresh_24),
                    contentDescription = "Odśwież"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_error_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (state.listeners.isEmpty() && !state.isLoading && state.error == null) {
            EmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.listeners) { listener ->
                    NearbyListenerCard(listener = listener)
                }
            }
        }
    }
}

@Composable
fun NowPlayingCard(
    track: String?,
    artist: String?,
    album: String?,
    modifier: Modifier = Modifier
) {
    val isPlaying = !track.isNullOrEmpty() && !artist.isNullOrEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.outline_music_note_24),
                contentDescription = null,
                tint = if (isPlaying)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Teraz grasz",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPlaying)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (isPlaying) {
                    Text(
                        text = track!!,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = artist!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (!album.isNullOrEmpty()) {
                        Text(
                            text = album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Text(
                        text = "W tej chwili nic nie słuchasz",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Uruchom Spotify lub YouTube Music",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun NearbyListenerCard(listener: NearbyListener) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = listener.nick,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.1f", listener.distance_km)} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_music_note_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = listener.track_name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = listener.artist_name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!listener.album_name.isNullOrEmpty()) {
                        Text(
                            text = listener.album_name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (listener.minutes_ago == 0) "Teraz" else "${listener.minutes_ago} min temu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.rounded_person_24),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Brak słuchaczy w pobliżu",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Upewnij się, że słuchasz muzyki i masz włączoną lokalizację",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}