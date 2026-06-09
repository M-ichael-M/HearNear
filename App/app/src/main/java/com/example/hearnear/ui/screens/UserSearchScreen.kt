package com.example.hearnear.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.hearnear.network.NearbyListener
import com.example.hearnear.network.NetworkModule
import com.example.hearnear.network.SearchedUser
import com.example.hearnear.ui.HearNearScreen
import com.example.hearnear.viewmodel.FriendsViewModel
import com.example.hearnear.viewmodel.NearbyListenersViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    friendsViewModel: FriendsViewModel,
    nearbyListenersViewModel: NearbyListenersViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState? = null
) {
    val token = remember {
        // pobieramy token przez SharedPrefs (tak samo jak w innych VM)
        null as String?
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchedUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Pobierz token z SharedPrefs przez context (musimy przekazać przez VM)
    val authToken = remember { friendsViewModel.getAuthToken() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Debounced search
    LaunchedEffect(query) {
        searchJob?.cancel()
        if (query.length < 2) {
            results = emptyList()
            errorMsg = null
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(350)
            isLoading = true
            errorMsg = null
            try {
                val response = NetworkModule.apiService.searchUsers(
                    "Bearer $authToken",
                    query
                )
                if (response.isSuccessful) {
                    results = response.body()?.users ?: emptyList()
                } else {
                    errorMsg = "Błąd wyszukiwania"
                }
            } catch (e: Exception) {
                errorMsg = "Błąd sieci: ${e.message}"
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pole wyszukiwania
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Szukaj po nicku…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Wyczyść")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focusRequester)
        )

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            query.length < 2 && query.isNotEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Wpisz co najmniej 2 znaki",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            query.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Szukaj użytkowników po nicku",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Nie znaleziono nikogo\no nicku $query",
                                    style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(results, key = { it.id }) { user ->
                        SearchResultCard(
                            user = user,
                            friendsViewModel = friendsViewModel,
                            snackbarHostState = snackbarHostState,
                            onOpenProfile = {
                                val fakeListener = NearbyListener(
                                    user_id = user.id,
                                    email = user.email,
                                    nick = user.nick,
                                    distance_km = -1.0,
                                    latitude = 0.0,
                                    longitude = 0.0,
                                    track_name = "",
                                    artist_name = "",
                                    album_name = null,
                                    last_updated = "",
                                    minutes_ago = 999,
                                    avatar_url = user.avatar_url,
                                    instagram_username = user.instagram_username,
                                    instagram_url = user.instagram_url
                                )
                                nearbyListenersViewModel._selectedListener.value = fakeListener
                                navController.navigate(HearNearScreen.OtherProfile.name)
                            },
                            onStatusChanged = { updatedUser ->
                                // Odśwież lokalnie status w liście bez nowego requesta
                                results = results.map { if (it.id == updatedUser.id) updatedUser else it }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    user: SearchedUser,
    friendsViewModel: FriendsViewModel,
    snackbarHostState: SnackbarHostState?,
    onOpenProfile: () -> Unit,
    onStatusChanged: (SearchedUser) -> Unit
) {
    var currentUser by remember { mutableStateOf(user) }
    var isActing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProfile() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
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
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (currentUser.avatar_url != null) {
                    AsyncImage(
                        model = "http://192.168.1.30:5000${currentUser.avatar_url}",
                        contentDescription = "Avatar ${currentUser.nick}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Nick + Instagram
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentUser.nick,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (currentUser.instagram_username != null) {
                    Text(
                        text = "@${currentUser.instagram_username}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Przycisk akcji znajomych
            if (isActing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FriendActionButton(
                    status = currentUser.friendship_status,
                    onAdd = {
                        isActing = true
                        friendsViewModel.sendFriendRequest(currentUser.id) { success, msg ->
                            isActing = false
                            if (success) {
                                val updated = currentUser.copy(
                                    friendship_status = "pending_sent"
                                )
                                currentUser = updated
                                onStatusChanged(updated)
                            }
                            scope.launch { snackbarHostState?.showSnackbar(msg) }
                        }
                    },
                    onCancel = {
                        val fid = currentUser.friendship_id ?: return@FriendActionButton
                        isActing = true
                        friendsViewModel.cancelFriendRequest(fid) { success, msg ->
                            isActing = false
                            if (success) {
                                val updated = currentUser.copy(
                                    friendship_status = "none",
                                    friendship_id = null
                                )
                                currentUser = updated
                                onStatusChanged(updated)
                            }
                            scope.launch { snackbarHostState?.showSnackbar(msg) }
                        }
                    },
                    onAccept = {
                        val fid = currentUser.friendship_id ?: return@FriendActionButton
                        isActing = true
                        friendsViewModel.acceptFriendRequest(fid) { success, msg ->
                            isActing = false
                            if (success) {
                                val updated = currentUser.copy(friendship_status = "accepted")
                                currentUser = updated
                                onStatusChanged(updated)
                            }
                            scope.launch { snackbarHostState?.showSnackbar(msg) }
                        }
                    },
                    onRemove = {
                        isActing = true
                        friendsViewModel.removeFriend(currentUser.id) { success, msg ->
                            isActing = false
                            if (success) {
                                val updated = currentUser.copy(
                                    friendship_status = "none",
                                    friendship_id = null
                                )
                                currentUser = updated
                                onStatusChanged(updated)
                            }
                            scope.launch { snackbarHostState?.showSnackbar(msg) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendActionButton(
    status: String,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onRemove: () -> Unit
) {
    when (status) {
        "none" -> FilledTonalButton(
            onClick = onAdd,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Dodaj", fontSize = 12.sp)
        }

        "pending_sent" -> OutlinedButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("Wysłano", fontSize = 12.sp)
        }

        "pending_received" -> FilledTonalButton(
            onClick = onAccept,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text("Akceptuj", fontSize = 12.sp)
        }

        "accepted" -> OutlinedButton(
            onClick = onRemove,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Usuń", fontSize = 12.sp)
        }
    }
}