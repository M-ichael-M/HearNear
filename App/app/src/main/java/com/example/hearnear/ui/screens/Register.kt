package com.example.hearnear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.hearnear.ui.HearNearScreen
import com.example.hearnear.viewmodel.AuthViewModel
import com.example.hearnear.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsState()

    var nick by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }

    // Walidacja pól
    val isNickValid = nick.length >= 3 && nick.length <= 50
    val isEmailValid = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isPasswordValid = password.length >= 6
    val doPasswordsMatch = password == confirmPassword && confirmPassword.isNotBlank()
    val isFormValid = isNickValid && isEmailValid && isPasswordValid && doPasswordsMatch && termsAccepted

    // Wyczyść błąd gdy użytkownik zaczyna pisać
    LaunchedEffect(nick, email, password, confirmPassword) {
        if (authState.error != null) {
            authViewModel.clearError()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Tytuł
        Text(
            text = "Zarejestruj się",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Pole nick
        OutlinedTextField(
            value = nick,
            onValueChange = { nick = it.trim() },
            label = { Text("Nick") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            isError = nick.isNotBlank() && !isNickValid,
            supportingText = {
                if (nick.isNotBlank() && !isNickValid) {
                    Text("Nick musi mieć 3-50 znaków")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
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
                        painter = if (passwordVisible) painterResource(R.drawable.outline_visibility_24)
                        else painterResource(R.drawable.outline_visibility_off_24),
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
                .padding(bottom = 16.dp),
            singleLine = true
        )

        // Pole potwierdzenie hasła
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Powtórz hasło") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { confirmPasswordVisible = !confirmPasswordVisible }
                ) {
                    Icon(
                        painter = if (confirmPasswordVisible) painterResource(R.drawable.outline_visibility_24) else painterResource(R.drawable.outline_visibility_off_24),
                        contentDescription = if (confirmPasswordVisible) "Ukryj hasło" else "Pokaż hasło"
                    )
                }
            },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = confirmPassword.isNotBlank() && !doPasswordsMatch,
            supportingText = {
                if (confirmPassword.isNotBlank() && !doPasswordsMatch) {
                    Text("Hasła nie są identyczne")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            singleLine = true
        )

        // Checkbox regulaminu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it }
            )
            Spacer(modifier = Modifier.width(8.dp))

            val annotatedText = buildAnnotatedString {
                append("Akceptuję ")

                pushStringAnnotation(tag = "TERMS", annotation = "regulamin")
                withStyle(
                    style = SpanStyle(
                        // np. niebieski
                        Color(0xFF2196F3), textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("regulamin")
                }
                pop()

                append(" aplikacji")
            }

            ClickableText(
                text = annotatedText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                        .firstOrNull()?.let {
                            navController.navigate(HearNearScreen.Statute.name)

                        }
                }
            )
        }

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

        // Przycisk rejestracji
        Button(
            onClick = {
                authViewModel.register(nick, email, password, termsAccepted)
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
                    text = "Zarejestruj się",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Link do logowania
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Masz już konto? ",
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = onNavigateToLogin,
                enabled = !authState.isLoading
            ) {
                Text(
                    text = "Zaloguj się",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}