package com.amos_tech_code.presentation.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class FCMTokenRequest(val token: String?)