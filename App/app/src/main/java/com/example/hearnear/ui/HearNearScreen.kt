package com.example.hearnear.ui

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hearnear.R
import com.example.hearnear.ui.screens.HomeScreen
import com.example.hearnear.ui.screens.LoginScreen
import com.example.hearnear.ui.screens.MapScreen
import com.example.hearnear.ui.screens.NotificationsScreen
import com.example.hearnear.ui.screens.OtherUserProfile
import com.example.hearnear.ui.screens.PrivacyPolicy
import com.example.hearnear.ui.screens.RegisterScreen
import com.example.hearnear.ui.screens.Statute
import com.example.hearnear.ui.screens.UserScreen
import com.example.hearnear.viewmodel.AuthViewModel
import com.example.hearnear.viewmodel.FriendsViewModel
import com.example.hearnear.viewmodel.NearbyListenersViewModel

enum class HearNearScreen(@StringRes val title: Int, @DrawableRes val imageRes: Int) {
    Start(title = R.string.start, imageRes = R.drawable.outline_home_24),
    Map(title = R.string.map, imageRes = R.drawable.outline_map_24),
    Profile(title = R.string.profile, imageRes = R.drawable.rounded_person_24),
    Login(title = R.string.login, imageRes = R.drawable.outline_login_24),
    Register(title = R.string.register, imageRes = R.drawable.outline_app_registration_24),
    PrivacyPolicy(title = R.string.privacy_policy, imageRes = R.drawable.rounded_person_24),
    Statute(title = R.string.statute, imageRes = R.drawable.outline_error_24),
    OtherProfile(title = R.string.other_profile, imageRes = R.drawable.rounded_person_24),
    Notifications(title = R.string.notifications, imageRes = R.drawable.outline_home_24)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearNearAppBar(
    currentScreen: HearNearScreen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    pendingCount: Int,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = when (currentScreen) {
                    HearNearScreen.Login -> "Logowanie"
                    HearNearScreen.Register -> "Rejestracja"
                    HearNearScreen.Notifications -> "Powiadomienia"
                    else -> stringResource(currentScreen.title)
                },
                color = MaterialTheme.colorScheme.onPrimary
            )
        },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        actions = {
            // Dzwonek z odznaczką – ukryj na ekranie powiadomień
            if (currentScreen != HearNearScreen.Notifications &&
                currentScreen != HearNearScreen.Login &&
                currentScreen != HearNearScreen.Register
            ) {
                IconButton(onClick = onNotificationsClick) {
                    BadgedBox(
                        badge = {
                            if (pendingCount > 0) {
                                Badge { Text(text = pendingCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Powiadomienia",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun HearNearBottomBar(
    navController: NavController,
    currentScreen: HearNearScreen,
    modifier: Modifier = Modifier
) {
    if (currentScreen == HearNearScreen.Login || currentScreen == HearNearScreen.Register) return

    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 25.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (currentScreen != HearNearScreen.Start) navController.navigate(HearNearScreen.Start.name) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = HearNearScreen.Start.imageRes),
                    contentDescription = stringResource(R.string.home),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    if (currentScreen != HearNearScreen.Map) navController.navigate(HearNearScreen.Map.name) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = HearNearScreen.Map.imageRes),
                    contentDescription = stringResource(R.string.map),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    if (currentScreen != HearNearScreen.Profile) navController.navigate(HearNearScreen.Profile.name) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = HearNearScreen.Profile.imageRes),
                    contentDescription = stringResource(R.string.profile),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HearNearApp(
    authViewModel: AuthViewModel,
    nearbyListenersViewModel: NearbyListenersViewModel,
    friendsViewModel: FriendsViewModel,
    navController: NavController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = HearNearScreen.valueOf(
        backStackEntry?.destination?.route ?: HearNearScreen.Start.name
    )
    val friendsState by friendsViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Odśwież powiadomienia przy starcie i gdy wrócisz na dowolny ekran
    LaunchedEffect(Unit) {
        friendsViewModel.loadPendingRequests()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HearNearAppBar(
                currentScreen = currentScreen,
                canNavigateBack = navController.previousBackStackEntry != null,
                navigateUp = { navController.popBackStack() },
                pendingCount = friendsState.pendingCount,
                onNotificationsClick = {
                    navController.navigate(HearNearScreen.Notifications.name)
                }
            )
        },
        bottomBar = {
            HearNearBottomBar(
                navController = navController,
                currentScreen = currentScreen
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController as NavHostController,
            startDestination = HearNearScreen.Start.name,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(HearNearScreen.Start.name) {
                HomeScreen(nearbyListenersViewModel = nearbyListenersViewModel)
            }
            composable(HearNearScreen.Map.name) {
                MapScreen(
                    nearbyListenersViewModel = nearbyListenersViewModel,
                    navController = navController
                )
            }
            composable(HearNearScreen.Profile.name) {
                UserScreen(
                    authViewModel = authViewModel,
                    navController = navController
                )
            }
            composable(HearNearScreen.Login.name) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onNavigateToRegister = { navController.navigate(HearNearScreen.Register.name) }
                )
            }
            composable(HearNearScreen.Register.name) {
                RegisterScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = { navController.popBackStack() },
                    navController = navController
                )
            }
            composable(HearNearScreen.PrivacyPolicy.name) { PrivacyPolicy() }
            composable(HearNearScreen.Statute.name) { Statute() }
            composable(HearNearScreen.Notifications.name) {
                NotificationsScreen(
                    friendsViewModel = friendsViewModel,
                    snackbarHostState = snackbarHostState
                )
            }
            composable(HearNearScreen.OtherProfile.name) {
                OtherUserProfile(
                    nearbyListenersViewModel = nearbyListenersViewModel,
                    friendsViewModel = friendsViewModel,
                    navController = navController,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}