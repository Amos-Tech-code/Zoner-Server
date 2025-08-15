package com.amos_tech_code.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val status: Int,
    val message: String
)

class AuthorizationException(message: String) : Exception(message)

class ConflictException(message: String) : Exception(message)

class AlreadyVerifiedException(message: String) : Exception(message)

class UsernameConflictException(val requestedUsername: String, val userId: String) : Exception()
