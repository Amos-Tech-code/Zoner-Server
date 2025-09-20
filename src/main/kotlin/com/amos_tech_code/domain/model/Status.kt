package com.amos_tech_code.domain.model

import java.time.LocalDateTime
import java.util.UUID

data class Status(
    val id: UUID,
    val userId: UUID,
    val mediaUrl: String,
    val mediaType: MediaType,
    val caption: String?,
    val blurHash: String?,
    val durationMillis: Long,
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deleted: Boolean = false,
    val deletedAt: LocalDateTime? = null,
    val version: Long = 0
)


data class StatusReply(
    val id: UUID,
    val statusId: UUID,
    val userId: UUID,
    val text: String,
    val mediaUrl: String?,
    val mediaType: MediaType?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deleted: Boolean = false,
    val deletedAt: LocalDateTime? = null,
    val version: Long = 0
)