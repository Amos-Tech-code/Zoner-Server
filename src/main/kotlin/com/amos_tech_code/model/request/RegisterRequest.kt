package com.amos_tech_code.model.request

import com.amos_tech_code.model.response.UserResponse
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val role: String? = null
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val currentStage: String,
    val nextAction: String,
    val isExistingUser: Boolean
)

@Serializable
data class VerifyEmailRequest(
    val userId: String,
    val code: String
)

@Serializable
data class ResendOtpRequest(val userId: String)

@Serializable
data class CompleteProfileRequest(
    val userId: String,
    val username: String,
    val profilePicUrl: String? = null  // Optional
)

@Serializable
data class CheckUserName(
    val userId: String,
    val username: String,
)

@Serializable
data class UsernameSuggestionResponse(
    val suggestions: List<String>
)

@Serializable
data class UsernameAvailabilityResponse(
    val available: Boolean,
    val suggestions: List<String>
)

data class ResendOtpResult(
    val email: String,
    val name: String,
    val code: String
)

data class CompleteProfileResult(val user: UserResponse)


sealed class VerificationResult {
    object Success : VerificationResult()
    data class Error(val reason: VerificationError) : VerificationResult()
}


data class RegisterResult(
    val userId: String,
    val currentStage: String,
    val nextAction: String,
    val verificationCode: String? = null,  // Only for internal use
    val isExistingUser: Boolean = false
) {
    fun toPublicResponse(): RegisterResponse {
        return RegisterResponse(
            userId = userId,
            currentStage = currentStage,
            nextAction = nextAction,
            isExistingUser = isExistingUser
        )
    }
}

enum class VerificationError {
    INVALID_CODE,
    EXPIRED_CODE,
    ALREADY_USED
}