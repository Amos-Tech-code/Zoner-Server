package com.amos_tech_code.presentation.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class StatusReactionRequest(val reactionType: String)

@Serializable
data class StatusCaptionRequest(val caption: String?)