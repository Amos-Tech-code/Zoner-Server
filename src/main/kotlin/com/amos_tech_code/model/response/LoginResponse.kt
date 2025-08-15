package com.amos_tech_code.model.response

import kotlinx.serialization.Serializable


@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String?,
    val username: String?,
    val profilePicUrl: String?,
    val role: String,
    val registrationStage: String,
    val businessProfile: BusinessProfileResponse?
)

@Serializable
data class BusinessProfileResponse(
    val businessName: String,
    val businessEmail: String?,
    val isVerified: Boolean,
    val businessLogo: String?
)