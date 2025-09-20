package com.amos_tech_code.domain.model

import java.util.UUID

data class UserBasicInfo(
    val id: UUID,
    val name: String?,
    val profilePicUrl: String?
)
