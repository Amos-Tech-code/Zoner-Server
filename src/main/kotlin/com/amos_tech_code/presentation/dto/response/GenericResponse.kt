package com.amos_tech_code.presentation.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class GenericResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)