package com.example.hearnear.viewmodel


import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearnear.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody

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

    fun updateInstagram(username: String?) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val token = getToken() ?: return@launch
            try {
                val response = NetworkModule.apiService.updateInstagram(
                    "Bearer $token",
                    InstagramRequest(username)
                )
                if (response.isSuccessful) {
                    val res = response.body()!!
                    _authState.value = _authState.value.copy(
                        user = _authState.value.user?.copy(
                            instagram_username = res.instagram_username,
                            instagram_url = res.instagram_url
                        ),
                        isLoading = false
                    )
                } else {
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = response.errorBody()?.string() ?: "Błąd"
                    )
                }
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val token = getToken() ?: return@launch
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = inputStream?.let { RequestBody.create("image/*".toMediaTypeOrNull(), it.readBytes()) }
                val part = MultipartBody.Part.createFormData("avatar", "avatar.jpg", file!!)
                val response = NetworkModule.apiService.uploadAvatar("Bearer $token", part)
                if (response.isSuccessful) {
                    val res = response.body()!!
                    _authState.value = _authState.value.copy(
                        user = _authState.value.user?.copy(avatar_url = res.avatar_url),
                        isLoading = false
                    )
                } else {
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = response.errorBody()?.string() ?: "Błąd uploadu"
                    )
                }
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val token = getToken() ?: return@launch
            try {
                val response = NetworkModule.apiService.deleteAvatar("Bearer $token")
                if (response.isSuccessful) {
                    _authState.value = _authState.value.copy(
                        user = _authState.value.user?.copy(avatar_url = null),
                        isLoading = false
                    )
                } else {
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = "Błąd usuwania"
                    )
                }
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = e.message
                )
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

    private fun clearAuthData() {
        sharedPrefs.edit()
            .remove("auth_token")
            .remove("user_data")
            .apply()
    }
}