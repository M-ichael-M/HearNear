package com.example.hearnear.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearnear.network.ApiError
import com.example.hearnear.network.NearbyListener
import com.example.hearnear.network.NetworkModule
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MapFilter { ALL, FRIENDS }

data class NearbyListenersState(
    val listeners: List<NearbyListener> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastRefreshTime: Long = 0L,
    val mapFilter: MapFilter = MapFilter.ALL
)

class NearbyListenersViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(NearbyListenersState())
    val state: StateFlow<NearbyListenersState> = _state.asStateFlow()

    val _selectedListener = MutableStateFlow<NearbyListener?>(null)
    val selectedListener: StateFlow<NearbyListener?> = _selectedListener.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun getToken(): String? {
        return sharedPrefs.getString("auth_token", null)
    }

    fun setMapFilter(filter: MapFilter) {
        _state.value = _state.value.copy(mapFilter = filter)
        when (filter) {
            MapFilter.ALL -> loadNearbyListeners()
            MapFilter.FRIENDS -> loadFriendsActivity()
        }
    }

    fun loadNearbyListeners(maxDistance: Double = 50.0, maxAgeMinutes: Int = 60) {
        val token = getToken()
        if (token == null) {
            _state.value = _state.value.copy(error = "Not authenticated")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val response = NetworkModule.apiService.getNearbyListeners(
                    "Bearer $token",
                    maxDistance,
                    maxAgeMinutes
                )

                if (response.isSuccessful) {
                    val nearbyResponse = response.body()!!
                    _state.value = _state.value.copy(
                        listeners = nearbyResponse.listeners,
                        isLoading = false,
                        error = null,
                        lastRefreshTime = System.currentTimeMillis()
                    )
                    Log.d("NearbyVM", "Loaded ${nearbyResponse.listeners.size} nearby listeners")
                } else {
                    val errorBody = response.errorBody()?.string()
                    val apiError = try {
                        gson.fromJson(errorBody, ApiError::class.java)
                    } catch (e: Exception) {
                        ApiError("Failed to load nearby listeners")
                    }
                    _state.value = _state.value.copy(isLoading = false, error = apiError.error)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
                Log.e("NearbyVM", "Network error", e)
            }
        }
    }

    fun loadFriendsActivity(maxAgeMinutes: Int = 60) {
        val token = getToken()
        if (token == null) {
            _state.value = _state.value.copy(error = "Not authenticated")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val response = NetworkModule.apiService.getFriendsActivity(
                    "Bearer $token",
                    maxAgeMinutes
                )

                if (response.isSuccessful) {
                    val body = response.body()!!
                    _state.value = _state.value.copy(
                        listeners = body.listeners,
                        isLoading = false,
                        error = null,
                        lastRefreshTime = System.currentTimeMillis()
                    )
                    Log.d("NearbyVM", "Loaded ${body.listeners.size} friends' activities")
                } else {
                    val errorBody = response.errorBody()?.string()
                    val apiError = try {
                        gson.fromJson(errorBody, ApiError::class.java)
                    } catch (e: Exception) {
                        ApiError("Failed to load friends activity")
                    }
                    _state.value = _state.value.copy(isLoading = false, error = apiError.error)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
                Log.e("NearbyVM", "loadFriendsActivity error", e)
            }
        }
    }

    fun refreshListeners() {
        when (_state.value.mapFilter) {
            MapFilter.ALL -> loadNearbyListeners()
            MapFilter.FRIENDS -> loadFriendsActivity()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30000)
                if (_state.value.listeners.isNotEmpty()) {
                    refreshListeners()
                }
            }
        }
    }
}