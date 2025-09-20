package com.amos_tech_code.data.database.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object NotificationsTable : Table("notifications") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id")
    val title = varchar("title", 255)
    val message = varchar("message", 1000)
    val type = varchar("type", 50) // etc.
    val postId = uuid("post_id").nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}