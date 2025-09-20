package com.amos_tech_code.presentation.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null
)