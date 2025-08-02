package com.example.hearnear.network


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
    val email: String
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

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<TokenVerifyResponse>

    @POST("api/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ApiError>
}
