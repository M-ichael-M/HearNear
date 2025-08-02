package com.example.hearnear


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hearnear.R
import com.example.hearnear.ui.screens.LoginScreen
import com.example.hearnear.ui.screens.RegisterScreen
import com.example.hearnear.viewmodel.AuthViewModel

enum class AuthScreen(val route: String) {
    Login("login"),
    Register("register")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthAppBar(
    currentScreen: AuthScreen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = when (currentScreen) {
                    AuthScreen.Login -> "Logowanie"
                    AuthScreen.Register -> "Rejestracja"
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
                        contentDescription = "Wstecz",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    )
}

@Composable
fun AuthApp(
    authViewModel: AuthViewModel,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = AuthScreen.entries.find {
        it.route == backStackEntry?.destination?.route
    } ?: AuthScreen.Login

    Column(modifier = Modifier.fillMaxSize()) {
        AuthAppBar(
            currentScreen = currentScreen,
            canNavigateBack = navController.previousBackStackEntry != null,
            navigateUp = { navController.popBackStack() }
        )

        NavHost(
            navController = navController,
            startDestination = AuthScreen.Login.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AuthScreen.Login.route) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onNavigateToRegister = {
                        navController.navigate(AuthScreen.Register.route)
                    }
                )
            }

            composable(AuthScreen.Register.route) {
                RegisterScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}