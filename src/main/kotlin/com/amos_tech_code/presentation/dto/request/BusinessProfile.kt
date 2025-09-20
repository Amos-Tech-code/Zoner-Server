package com.amos_tech_code.presentation.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateBusinessProfile(
    val businessName: String,
    val category: String,
    val phoneNumber: String,
    val description: String,
    val location: String,
    val country: String,
    val isTermsAccepted: Boolean
)