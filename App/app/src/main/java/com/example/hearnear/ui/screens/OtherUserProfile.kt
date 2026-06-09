package com.example.hearnear.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.hearnear.viewmodel.FriendsViewModel
import com.example.hearnear.viewmodel.NearbyListenersViewModel
import com.example.hearnear.R
import kotlinx.coroutines.launch

@Composable
fun OtherUserProfile(
    nearbyListenersViewModel: NearbyListenersViewModel,
    friendsViewModel: FriendsViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState? = null
) {
    val selectedListener by nearbyListenersViewModel.selectedListener.collectAsState()
    val friendshipStatus by friendsViewModel.profileFriendshipStatus.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isActing by remember { mutableStateOf(false) }

    if (selectedListener == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val listener = selectedListener!!

    // Załaduj status relacji gdy znamy user_id (nick jako fallback – serwer musi zwracać id)
    // NearbyListener nie ma pola id – musimy go mieć, dlatego rozszerzyliśmy model
    LaunchedEffect(listener.nick) {
        // listener.user_id może być null jeśli backend nie zwraca go jeszcze;
        // w takim przypadku przycisk będzie ukryty
        val uid = listener.user_id
        if (uid != null) {
            friendsViewModel.loadFriendshipStatus(uid)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Karta profilu
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = listener.avatar_url
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = "http://192.168.1.30:5000$avatarUrl",
                            contentDescription = "Awatar użytkownika",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Awatar użytkownika",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = listener.nick,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Odległość: ${String.format("%.1f", listener.distance_km)} km",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Przycisk znajomych
                val uid = listener.user_id
                if (uid != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    when (friendshipStatus?.status) {
                        "none", null -> {
                            Button(
                                onClick = {
                                    isActing = true
                                    friendsViewModel.sendFriendRequest(uid) { success, msg ->
                                        isActing = false
                                        scope.launch { snackbarHostState?.showSnackbar(msg) }
                                    }
                                },
                                enabled = !isActing
                            ) {
                                if (isActing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dodaj do znajomych")
                                }
                            }
                        }

                        "pending_sent" -> {
                            OutlinedButton(
                                onClick = {
                                    val fid = friendshipStatus?.friendship_id ?: return@OutlinedButton
                                    isActing = true
                                    friendsViewModel.cancelFriendRequest(fid) { success, msg ->
                                        isActing = false
                                        scope.launch { snackbarHostState?.showSnackbar(msg) }
                                    }
                                },
                                enabled = !isActing
                            ) {
                                Text("Anuluj zaproszenie")
                            }
                        }

                        "pending_received" -> {
                            // Można zaakceptować bezpośrednio z tego widoku
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val fid = friendshipStatus?.friendship_id ?: return@Button
                                        isActing = true
                                        friendsViewModel.acceptFriendRequest(fid) { success, msg ->
                                            isActing = false
                                            scope.launch { snackbarHostState?.showSnackbar(msg) }
                                        }
                                    },
                                    enabled = !isActing
                                ) {
                                    Text("Akceptuj")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val fid = friendshipStatus?.friendship_id ?: return@OutlinedButton
                                        isActing = true
                                        friendsViewModel.declineFriendRequest(fid) { success, msg ->
                                            isActing = false
                                            scope.launch { snackbarHostState?.showSnackbar(msg) }
                                        }
                                    },
                                    enabled = !isActing
                                ) {
                                    Text("Odrzuć")
                                }
                            }
                        }

                        "accepted" -> {
                            OutlinedButton(
                                onClick = {
                                    isActing = true
                                    friendsViewModel.removeFriend(uid) { success, msg ->
                                        isActing = false
                                        scope.launch { snackbarHostState?.showSnackbar(msg) }
                                    }
                                },
                                enabled = !isActing,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Usuń ze znajomych")
                            }
                        }
                    }
                }
            }
        }

        // Karta muzyki
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aktualnie słucha",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (listener.minutes_ago < 5 && listener.track_name.isNotEmpty()) {
                    UserInfoItem(
                        icon = Icons.Default.Menu,
                        label = "Utwór",
                        value = listener.track_name
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    UserInfoItem(
                        icon = Icons.Default.Person,
                        label = "Artysta",
                        value = listener.artist_name
                    )
                    if (listener.album_name?.isNotEmpty() == true) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        UserInfoItem(
                            icon = Icons.Default.Menu,
                            label = "Album",
                            value = listener.album_name ?: ""
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Ostatnia aktualizacja: ${if (listener.minutes_ago == 0) "Teraz" else "${listener.minutes_ago} min temu"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Użytkownik nie słucha niczego obecnie.",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Social media
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Social media",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.instagram2),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Instagram",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val igUrl = listener.instagram_url
                        if (igUrl != null) {
                            Text(
                                text = "@${listener.instagram_username}",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(igUrl))
                                    context.startActivity(intent)
                                }
                            )
                        } else {
                            Text(
                                text = "Użytkownik nie podpiął IG",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}