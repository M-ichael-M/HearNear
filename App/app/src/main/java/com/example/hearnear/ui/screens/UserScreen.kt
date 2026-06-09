package com.example.hearnear.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.hearnear.ui.HearNearScreen
import com.example.hearnear.viewmodel.AuthViewModel
import com.example.hearnear.viewmodel.FriendsViewModel
import kotlinx.coroutines.launch
import com.example.hearnear.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    authViewModel: AuthViewModel? = null,
    friendsViewModel: FriendsViewModel? = null,
    navController: NavController
) {
    val authState by authViewModel?.authState?.collectAsState() ?: remember { mutableStateOf(null) }
    val friendsState by friendsViewModel?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val showInstagramDialog = remember { mutableStateOf(false) }
    var instagramInput by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            authViewModel?.uploadAvatar(uri)
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Odśwież liczbę znajomych przy wejściu na ekran
    LaunchedEffect(Unit) {
        friendsViewModel?.loadFriends()
    }

    if (showInstagramDialog.value) {
        AlertDialog(
            onDismissRequest = { showInstagramDialog.value = false },
            title = { Text("Dodaj/Edytuj Instagram") },
            text = {
                TextField(
                    value = instagramInput,
                    onValueChange = { instagramInput = it },
                    label = { Text("Nazwa użytkownika IG") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    authViewModel?.updateInstagram(if (instagramInput.isBlank()) null else instagramInput)
                    showInstagramDialog.value = false
                }) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                Button(onClick = { showInstagramDialog.value = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Karta profilu ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
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
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (authState?.user?.avatar_url != null) {
                                coroutineScope.launch {
                                    authViewModel?.deleteAvatar()
                                }
                            } else {
                                launcher.launch("image/*")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = authState?.user?.avatar_url
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (authState?.user?.avatar_url != null) "Kliknij by usunąć awatar" else "Kliknij by dodać awatar",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = authState?.user?.nick ?: "Użytkownik",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = authState?.user?.email ?: "user@example.com",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // --- Licznik znajomych ---
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                val friendsCount = friendsState?.friends?.size ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Kafelek znajomych
                    Surface(
                        onClick = { navController.navigate(HearNearScreen.Friends.name) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Znajomi",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (friendsCount == 1) "1 osoba" else "$friendsCount osób",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.outline_arrow_back_24),
                                contentDescription = "Przejdź do znajomych",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(180f)
                            )
                        }
                    }

                    // Przycisk wyszukiwania
                    Surface(
                        onClick = { navController.navigate(HearNearScreen.UserSearch.name) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Szukaj użytkowników",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Karta: Informacje o koncie ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Informacje o koncie",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                UserInfoItem(
                    icon = Icons.Default.Person,
                    label = "Nick",
                    value = authState?.user?.nick ?: "Brak danych"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                UserInfoItem(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = authState?.user?.email ?: "Brak danych"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            instagramInput = authState?.user?.instagram_username ?: ""
                            showInstagramDialog.value = true
                        },
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
                        val igUrl = authState?.user?.instagram_url
                        if (igUrl != null) {
                            Text(
                                text = "@${authState?.user?.instagram_username}",
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
                                text = "Dodaj profil Instagram",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // --- Karta: Opcje ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Opcje",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                UserActionItem(
                    icon = Icons.Default.Person,
                    label = "Privacy & Policy",
                    onClick = {
                        navController.navigate(HearNearScreen.PrivacyPolicy.name)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                UserActionItem(
                    icon = Icons.Default.Info,
                    label = "Statute",
                    onClick = {
                        navController.navigate(HearNearScreen.Statute.name)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                UserActionItem(
                    icon = Icons.Default.ExitToApp,
                    label = "Wyloguj się",
                    onClick = {
                        authViewModel?.logout()
                    },
                    isDestructive = true
                )
            }
        }

        // --- Stopka ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HearNear",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Wersja 1.0.0",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun UserInfoItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun UserActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                fontSize = 16.sp,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}