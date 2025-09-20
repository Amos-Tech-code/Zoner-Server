package com.amos_tech_code.presentation.dto.request

import kotlinx.serialization.Serializable


@Serializable
data class ResetPasswordRequest(
    val email: String? = null,
    val otp: String? = null,
    val newPassword: String? = null
)


@Serializable
data class ForgotPasswordRequest(val email: String? = null)