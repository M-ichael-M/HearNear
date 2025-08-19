package com.example.hearnear


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hearnear.ui.screens.LoginScreen
import com.example.hearnear.ui.screens.RegisterScreen
import com.example.hearnear.ui.screens.Statute
import com.example.hearnear.viewmodel.AuthViewModel

enum class AuthScreen(val route: String) {
    Login("login"),
    Register("register"),
    Statute("Statute")
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
                    AuthScreen.Statute -> "Regulamin"
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
                    },
                    navController = navController
                )
            }

            composable(AuthScreen.Statute.route) {
                Statute()
            }
        }
    }
}