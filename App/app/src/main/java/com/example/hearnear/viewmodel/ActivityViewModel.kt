package com.example.hearnear.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearnear.network.ActivityData
import com.example.hearnear.network.ApiError
import com.example.hearnear.network.NetworkModule
import com.example.hearnear.network.UpdateActivityRequest
import com.example.hearnear.service.LocationService
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicData(
    val trackName: String,
    val artistName: String,
    val albumName: String? = null
)

data class ActivityState(
    val isUpdating: Boolean = false,
    val lastActivity: ActivityData? = null,
    val error: String? = null
)

class ActivityViewModel(private val context: Context) : ViewModel() {

    private val _activityState = MutableStateFlow(ActivityState())
    val activityState: StateFlow<ActivityState> = _activityState.asStateFlow()

    private val locationService = LocationService(context)
    private val sharedPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun getToken(): String? {
        return sharedPrefs.getString("auth_token", null)
    }

    fun updateActivity(musicData: MusicData) {
        val token = getToken()
        if (token == null) {
            _activityState.value = _activityState.value.copy(
                error = "Not authenticated"
            )
            return
        }

        _activityState.value = _activityState.value.copy(isUpdating = true, error = null)

        locationService.getCurrentLocation { locationData ->
            if (locationData == null) {
                _activityState.value = _activityState.value.copy(
                    isUpdating = false,
                    error = "Could not get location"
                )
                return@getCurrentLocation
            }

            viewModelScope.launch {
                try {
                    val request = UpdateActivityRequest(
                        latitude = locationData.latitude,
                        longitude = locationData.longitude,
                        track_name = musicData.trackName,
                        artist_name = musicData.artistName,
                        album_name = musicData.albumName
                    )

                    val response = NetworkModule.apiService.updateActivity(
                        "Bearer $token",
                        request
                    )

                    if (response.isSuccessful) {
                        val activityResponse = response.body()!!
                        _activityState.value = _activityState.value.copy(
                            isUpdating = false,
                            lastActivity = activityResponse.activity,
                            error = null
                        )
                        Log.d("ActivityVM", "Activity updated successfully")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val apiError = try {
                            gson.fromJson(errorBody, ApiError::class.java)
                        } catch (e: Exception) {
                            ApiError("Failed to update activity")
                        }

                        _activityState.value = _activityState.value.copy(
                            isUpdating = false,
                            error = apiError.error
                        )
                        Log.e("ActivityVM", "Failed to update activity: ${apiError.error}")
                    }
                } catch (e: Exception) {
                    _activityState.value = _activityState.value.copy(
                        isUpdating = false,
                        error = "Network error: ${e.message}"
                    )
                    Log.e("ActivityVM", "Network error", e)
                }
            }
        }
    }

    fun clearError() {
        _activityState.value = _activityState.value.copy(error = null)
    }
}