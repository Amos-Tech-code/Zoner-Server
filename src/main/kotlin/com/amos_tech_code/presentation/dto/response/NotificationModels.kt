package com.amos_tech_code.presentation.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    val notifications: List<Notification>,
    val unreadCount: Int
)

@Serializable
data class Notification(
    val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: String,
    val orderId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String
)