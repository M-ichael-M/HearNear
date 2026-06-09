package com.example.hearnear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.hearnear.network.FriendEntry
import com.example.hearnear.network.NearbyListener
import com.example.hearnear.ui.HearNearScreen
import com.example.hearnear.viewmodel.FriendsViewModel
import com.example.hearnear.viewmodel.NearbyListenersViewModel

@Composable
fun FriendsScreen(
    friendsViewModel: FriendsViewModel,
    nearbyListenersViewModel: NearbyListenersViewModel,
    navController: NavController
) {
    val state by friendsViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        friendsViewModel.loadFriends()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Znajomi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.friends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Brak znajomych",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Znajdź słuchaczy na mapie i dodaj ich do znajomych",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.friends, key = { it.friendship_id }) { entry ->
                    FriendCard(
                        entry = entry,
                        onClick = {
                            // Konwertuj FriendEntry na NearbyListener (pola muzyczne puste – profil i tak pokaże dane)
                            val fakeListener = NearbyListener(
                                user_id = entry.user.id,
                                email = entry.user.email,
                                nick = entry.user.nick,
                                distance_km = 0.0,
                                latitude = 0.0,
                                longitude = 0.0,
                                track_name = "",
                                artist_name = "",
                                album_name = null,
                                last_updated = "",
                                minutes_ago = 999,
                                avatar_url = entry.user.avatar_url,
                                instagram_username = entry.user.instagram_username,
                                instagram_url = entry.user.instagram_url
                            )
                            nearbyListenersViewModel._selectedListener.value = fakeListener
                            navController.navigate(HearNearScreen.OtherProfile.name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendCard(
    entry: FriendEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val avatarUrl = entry.user.avatar_url
                if (avatarUrl != null) {
                    AsyncImage(
                        model = "http://192.168.1.30:5000$avatarUrl",
                        contentDescription = "Avatar ${entry.user.nick}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.user.nick,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (entry.user.instagram_username != null) {
                    Text(
                        text = "@${entry.user.instagram_username}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = entry.user.email,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Zobacz profil",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}