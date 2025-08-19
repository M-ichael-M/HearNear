package com.example.hearnear.network


import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

// Data classes dla API
data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val nick: String,
    val email: String,
    val password: String,
    val terms_accepted: Boolean
)

data class User(
    val id: Int,
    val nick: String,
    val email: String,
    val instagram_username: String? = null,
    val instagram_url: String? = null,
    val avatar_url: String? = null
)

data class AuthResponse(
    val message: String,
    val token: String,
    val user: User
)

data class ApiError(
    val error: String
)

data class TokenVerifyResponse(
    val valid: Boolean,
    val user: User
)

data class InstagramRequest(
    val instagram_username: String?
)

data class InstagramResponse(
    val message: String,
    val instagram_username: String?,
    val instagram_url: String?
)

data class AvatarResponse(
    val message: String,
    val avatar_url: String?
)

data class UpdateActivityRequest(
    val latitude: Double,
    val longitude: Double,
    val track_name: String,
    val artist_name: String,
    val album_name: String? = null
)

data class ActivityResponse(
    val message: String,
    val activity: ActivityData
)

data class ActivityData(
    val latitude: Double,
    val longitude: Double,
    val track_name: String,
    val artist_name: String,
    val album_name: String?,
    val last_updated: String
)

data class NearbyListener(
    val email: String,
    val nick: String,
    val distance_km: Double,
    val latitude: Double,
    val longitude: Double,
    val track_name: String,
    val artist_name: String,
    val album_name: String?,
    val last_updated: String,
    val minutes_ago: Int
)

data class NearbyListenersResponse(
    val listeners: List<NearbyListener>,
    val total_count: Int,
    val search_params: SearchParams
)

data class SearchParams(
    val max_distance_km: Double,
    val max_age_minutes: Int,
    val your_location: YourLocation
)

data class YourLocation(
    val latitude: Double,
    val longitude: Double
)

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<TokenVerifyResponse>

    @POST("api/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ApiError>

    @POST("api/update-activity")
    suspend fun updateActivity(
        @Header("Authorization") token: String,
        @Body request: UpdateActivityRequest
    ): Response<ActivityResponse>

    @GET("api/nearby-listeners")
    suspend fun getNearbyListeners(
        @Header("Authorization") token: String,
        @Query("max_distance") maxDistance: Double = 50.0,
        @Query("max_age_minutes") maxAgeMinutes: Int = 60
    ): Response<NearbyListenersResponse>

    @POST("api/instagram")
    suspend fun updateInstagram(
        @Header("Authorization") token: String,
        @Body request: InstagramRequest
    ): Response<InstagramResponse>

    @Multipart
    @POST("api/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") token: String,
        @Part avatar: MultipartBody.Part
    ): Response<AvatarResponse>

    @DELETE("api/avatar")
    suspend fun deleteAvatar(
        @Header("Authorization") token: String
    ): Response<ApiError>
}