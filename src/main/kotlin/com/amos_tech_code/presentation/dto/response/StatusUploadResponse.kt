package com.amos_tech_code.presentation.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class UserStatusResponse(
    val statuses: List<StatusUploadResponse>
)

@Serializable
data class StatusUploadResponse(
    val id: String,                 // serverId (UUID as string)
    val mediaUrl: String,           // final hosted media URL
    val caption: String?,
    val mediaType: String,          // Enum name as string (IMAGE, VIDEO)
    val blurHash: String?,   // BlurHash string for image previews
    val durationMillis: Long,   // Duration in milliseconds for videos
    val viewCount: Int,         // Initial engagement count
    val likeCount: Int,
    val replyCount: Int,
    val createdAt: Long,            // server timestamp (epoch milliseconds)
    val lastUpdated: Long,          // server-side last update (epoch milliseconds)
    val expiresAt: Long,            // expiration timestamp (epoch milliseconds)
    val version: Long,           // optimistic concurrency version
)