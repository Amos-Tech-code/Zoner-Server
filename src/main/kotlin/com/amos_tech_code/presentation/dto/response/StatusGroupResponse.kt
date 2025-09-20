package com.amos_tech_code.presentation.dto.response

import com.amos_tech_code.domain.model.MediaType
import kotlinx.serialization.Serializable

@Serializable
data class StatusGroupsResponseDto (
    val statusGroups: List<StatusGroup>,
    val hasMore: Boolean = false,
    val totalPages: Int = 0, // -1 for recommendation mode (unknown total)
    val currentPage: Int = 1,
)

@Serializable
data class StatusGroup (
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val statuses: List<OtherUserStatus>,
    val updatedAt: Long, // Timestamp for most recent updated status
    val unviewedCount: Int = 0,
)

@Serializable
data class OtherUserStatus(
    val id: String,
    val mediaUrl: String,
    val caption: String?,
    val mediaType: MediaType,
    val createdAt: Long,
    val isViewed: Boolean,
    val blurHash: String?,
    val durationMillis: Long,
    val likesCount: Int,
    val viewsCount: Int,
    val repliesCount: Int,
    val expiresAt: Long,
    val lastUpdated: Long,
    val version: Long
)