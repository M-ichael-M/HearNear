package com.example.hearnear.viewmodel


import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearnear.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.Gson

data class AuthState(
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(private val context: Context) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()

    init {
        checkSavedToken()
    }

    private fun checkSavedToken() {
        val token = getToken()
        if (token != null) {
            verifyToken(token)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            try {
                val response = NetworkModule.apiService.login(
                    LoginRequest(email, password)
                )

                if (response.isSuccessful) {
                    val authResponse = response.body()!!
                    saveToken(authResponse.token)
                    saveUser(authResponse.user)

                    _authState.value = _authState.value.copy(
                        isLoggedIn = true,
                        user = authResponse.user,
                        isLoading = false,
                        error = null
                    )
                } else {
                    val errorBody = response.errorBody()?.string()
                    val apiError = try {
                        gson.fromJson(errorBody, ApiError::class.java)
                    } catch (e: Exception) {
                        ApiError("Login failed")
                    }

                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = apiError.error
                    )
                }
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun register(nick: String, email: String, password: String, termsAccepted: Boolean) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            try {
                val response = NetworkModule.apiService.register(
                    RegisterRequest(nick, email, password, termsAccepted)
                )

                if (response.isSuccessful) {
                    val authResponse = response.body()!!
                    saveToken(authResponse.token)
                    saveUser(authResponse.user)

                    _authState.value = _authState.value.copy(
                        isLoggedIn = true,
                        user = authResponse.user,
                        isLoading = false,
                        error = null
                    )
                } else {
                    val errorBody = response.errorBody()?.string()
                    val apiError = try {
                        gson.fromJson(errorBody, ApiError::class.java)
                    } catch (e: Exception) {
                        ApiError("Registration failed")
                    }

                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = apiError.error
                    )
                }
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    private fun verifyToken(token: String) {
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.verifyToken("Bearer $token")

                if (response.isSuccessful) {
                    val verifyResponse = response.body()!!
                    if (verifyResponse.valid) {
                        _authState.value = _authState.value.copy(
                            isLoggedIn = true,
                            user = verifyResponse.user
                        )
                    } else {
                        clearAuthData()
                    }
                } else {
                    clearAuthData()
                }
            } catch (e: Exception) {
                clearAuthData()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = getToken()
            if (token != null) {
                try {
                    NetworkModule.apiService.logout("Bearer $token")
                } catch (e: Exception) {
                    // Ignorujemy błędy logout - i tak czyścimy lokalne dane
                }
            }

            clearAuthData()
            _authState.value = AuthState()
        }
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    private fun saveToken(token: String) {
        sharedPrefs.edit().putString("auth_token", token).apply()
    }

    private fun getToken(): String? {
        return sharedPrefs.getString("auth_token", null)
    }

    private fun saveUser(user: User) {
        val userJson = gson.toJson(user)
        sharedPrefs.edit().putString("user_data", userJson).apply()
    }

    private fun getUser(): User? {
        val userJson = sharedPrefs.getString("user_data", null)
        return if (userJson != null) {
            try {
                gson.fromJson(userJson, User::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    private fun clearAuthData() {
        sharedPrefs.edit()
            .remove("auth_token")
            .remove("user_data")
            .apply()
    }
}