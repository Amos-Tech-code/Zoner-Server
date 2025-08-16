package com.amos_tech_code.model.request

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null
)