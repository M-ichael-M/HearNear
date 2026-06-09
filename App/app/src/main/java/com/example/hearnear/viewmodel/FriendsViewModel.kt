package com.example.hearnear.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearnear.network.FriendEntry
import com.example.hearnear.network.FriendshipStatusResponse
import com.example.hearnear.network.NetworkModule
import com.example.hearnear.network.PendingReceivedEntry
import com.example.hearnear.network.PendingSentEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsState(
    val friends: List<FriendEntry> = emptyList(),
    val pendingReceived: List<PendingReceivedEntry> = emptyList(),
    val pendingSent: List<PendingSentEntry> = emptyList(),
    val pendingCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class FriendsViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    // Status relacji z aktualnie oglądanym profilem
    private val _profileFriendshipStatus = MutableStateFlow<FriendshipStatusResponse?>(null)
    val profileFriendshipStatus: StateFlow<FriendshipStatusResponse?> = _profileFriendshipStatus.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private fun getToken(): String? = sharedPrefs.getString("auth_token", null)

    // ----- Ładowanie danych -----

    fun loadAll() {
        loadFriends()
        loadPendingRequests()
    }

    fun loadFriends() {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getFriends("Bearer $token")
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _state.value = _state.value.copy(friends = body.friends, error = null)
                }
            } catch (e: Exception) {
                Log.e("FriendsVM", "loadFriends error", e)
            }
        }
    }

    fun loadPendingRequests() {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getPendingRequests("Bearer $token")
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _state.value = _state.value.copy(
                        pendingReceived = body.received,
                        pendingSent = body.sent,
                        pendingCount = body.pending_count,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e("FriendsVM", "loadPending error", e)
            }
        }
    }

    fun loadFriendshipStatus(targetUserId: Int) {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getFriendshipStatus("Bearer $token", targetUserId)
                if (response.isSuccessful) {
                    _profileFriendshipStatus.value = response.body()
                }
            } catch (e: Exception) {
                Log.e("FriendsVM", "loadFriendshipStatus error", e)
            }
        }
    }

    fun clearProfileFriendshipStatus() {
        _profileFriendshipStatus.value = null
    }

    // ----- Akcje -----

    fun sendFriendRequest(targetUserId: Int, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.sendFriendRequest("Bearer $token", targetUserId)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _profileFriendshipStatus.value = FriendshipStatusResponse(
                        status = "pending_sent",
                        friendship_id = body.friendship_id
                    )
                    loadPendingRequests()
                    onResult(true, "Zaproszenie wysłane")
                } else {
                    val msg = response.errorBody()?.string() ?: "Błąd wysyłania zaproszenia"
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Błąd sieci")
            }
        }
    }

    fun acceptFriendRequest(friendshipId: Int, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.acceptFriendRequest("Bearer $token", friendshipId)
                if (response.isSuccessful) {
                    loadAll()
                    onResult(true, "Znajomy dodany!")
                } else {
                    onResult(false, response.errorBody()?.string() ?: "Błąd akceptacji")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Błąd sieci")
            }
        }
    }

    fun declineFriendRequest(friendshipId: Int, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.declineFriendRequest("Bearer $token", friendshipId)
                if (response.isSuccessful) {
                    loadAll()
                    onResult(true, "Zaproszenie odrzucone")
                } else {
                    onResult(false, response.errorBody()?.string() ?: "Błąd odrzucania")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Błąd sieci")
            }
        }
    }

    fun removeFriend(targetUserId: Int, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.removeFriend("Bearer $token", targetUserId)
                if (response.isSuccessful) {
                    _profileFriendshipStatus.value = FriendshipStatusResponse(status = "none", friendship_id = null)
                    loadAll()
                    onResult(true, "Znajomy usunięty")
                } else {
                    onResult(false, response.errorBody()?.string() ?: "Błąd usuwania")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Błąd sieci")
            }
        }
    }

    fun cancelFriendRequest(friendshipId: Int, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.declineFriendRequest("Bearer $token", friendshipId)
                if (response.isSuccessful) {
                    _profileFriendshipStatus.value = FriendshipStatusResponse(status = "none", friendship_id = null)
                    loadPendingRequests()
                    onResult(true, "Zaproszenie anulowane")
                } else {
                    onResult(false, response.errorBody()?.string() ?: "Błąd")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Błąd sieci")
            }
        }
    }

    fun getAuthToken(): String? = getToken()
}