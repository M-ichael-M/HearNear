package com.example.hearnear.ui.screens


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hearnear.viewmodel.AuthViewModel
import com.example.hearnear.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Walidacja pól
    val isEmailValid = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isPasswordValid = password.length >= 6
    val isFormValid = isEmailValid && isPasswordValid

    // Wyczyść błąd gdy użytkownik zaczyna pisać
    LaunchedEffect(email, password) {
        if (authState.error != null) {
            authViewModel.clearError()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Tytuł
        Text(
            text = "Zaloguj się",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Pole email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = email.isNotBlank() && !isEmailValid,
            supportingText = {
                if (email.isNotBlank() && !isEmailValid) {
                    Text("Nieprawidłowy format email")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        // Pole hasło
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible }
                ) {
                    Icon(
                        painter = if (passwordVisible) painterResource(R.drawable.outline_visibility_24) else painterResource(R.drawable.outline_visibility_off_24),
                        contentDescription = if (passwordVisible) "Ukryj hasło" else "Pokaż hasło"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = password.isNotBlank() && !isPasswordValid,
            supportingText = {
                if (password.isNotBlank() && !isPasswordValid) {
                    Text("Hasło musi mieć minimum 6 znaków")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            singleLine = true
        )

        // Komunikat błędu
        if (authState.error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = authState.error!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Przycisk logowania
        Button(
            onClick = {
                authViewModel.login(email, password)
            },
            enabled = isFormValid && !authState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Zaloguj się",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Link do rejestracji
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nie masz konta? ",
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = onNavigateToRegister,
                enabled = !authState.isLoading
            ) {
                Text(
                    text = "Zarejestruj się",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}