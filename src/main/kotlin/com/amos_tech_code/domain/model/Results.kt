package com.amos_tech_code.domain.model

import java.util.UUID


sealed class ResetPasswordResult {
    object Success : ResetPasswordResult()
    object UserNotFound : ResetPasswordResult()
    object InvalidRequest : ResetPasswordResult()
    object InvalidOtp : ResetPasswordResult()
}

data class User(
    val id: UUID,
    val email: String,
    val name: String?,
    val authProvider: AuthProvider,
    val registrationStage: RegistrationStage
)