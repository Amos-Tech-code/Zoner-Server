package com.amos_tech_code.model.request

import kotlinx.serialization.Serializable

@Serializable
data class FCMTokenRequest(val token: String?)