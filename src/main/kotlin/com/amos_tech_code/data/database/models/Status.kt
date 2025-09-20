package com.amos_tech_code.data.database.models

import com.amos_tech_code.domain.model.MediaType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime.now

object StatusTable : Table("statuses") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id") references UsersTable.id

    val mediaUrl = text("media_url")
    val mediaType = enumerationByName("media_type", 20, MediaType::class)
    val caption = text("caption").nullable()
    val blurHash = text("blur_hash").nullable()
    val durationMillis = long("duration_millis").default(0L)
    // Engagement metrics
    val viewCount = integer("view_count").default(0)
    val replyCount = integer("reply_count").default(0)
    val likeCount = integer("like_count").default(0)

    val expiresAt = datetime("expires_at")

    val createdAt = datetime("created_at").clientDefault { now() }
    val updatedAt = datetime("updated_at").clientDefault { now() }

    val deleted = bool("deleted").default(false)
    val deletedAt = datetime("deletedAt").nullable()
    val version = long("version").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        // Critical indexes for query performance
        index(isUnique = false, columns = arrayOf(userId, createdAt)) // For user status queries
        index(isUnique = false, columns = arrayOf(userId, expiresAt, deleted)) // For active status queries
        index(isUnique = false, columns = arrayOf(expiresAt, deleted)) // For cleanup queries
        index(isUnique = false, columns = arrayOf(createdAt)) // For recent status queries
        index(isUnique = false, columns = arrayOf(userId, mediaType)) // For media type filtering
        index(isUnique = false, columns = arrayOf(deleted)) // For soft deletion filtering
    }
}

object StatusViewsTable : Table("status_views") {
    val id = uuid("id").autoGenerate()
    val statusId = uuid("status_id") references StatusTable.id
    val viewerId = uuid("viewer_id") references UsersTable.id
    val viewedAt = datetime("viewed_at").clientDefault { now() }
    val viewDurationMillis = long("view_duration_millis").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(statusId, viewerId) // Prevent duplicate views from same user

        // Additional performance indexes
        index(isUnique = false, columns = arrayOf(viewerId, statusId)) // For user view checks
        index(isUnique = false, columns = arrayOf(viewerId, viewedAt)) // For user activity queries
        index(isUnique = false, columns = arrayOf(statusId, viewedAt)) // For status analytics
        index(isUnique = false, columns = arrayOf(viewerId)) // For user-specific queries
        index(isUnique = false, columns = arrayOf(statusId)) // For status-specific queries
    }
}

object StatusLikesTable : Table("status_likes") {
    val id = uuid("id").autoGenerate()
    val statusId = uuid("status_id") references StatusTable.id
    val userId = uuid("user_id") references UsersTable.id
    val createdAt = datetime("created_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(statusId, userId) // One like per user per status

        // Additional performance indexes
        index(isUnique = false, columns = arrayOf(userId, statusId)) // For user like checks
        index(isUnique = false, columns = arrayOf(userId, createdAt)) // For user activity
        index(isUnique = false, columns = arrayOf(statusId, createdAt)) // For status popularity
        index(isUnique = false, columns = arrayOf(userId)) // For user-specific queries
        index(isUnique = false, columns = arrayOf(statusId)) // For status-specific queries
    }
}


object StatusRepliesTable : Table("status_replies") {
    val id = uuid("id").autoGenerate()
    val statusId = uuid("status_id") references StatusTable.id
    val userId = uuid("user_id") references UsersTable.id
    val text = text("text")
    val mediaUrl = text("media_url").nullable()
    val mediaType = enumerationByName("media_type", 20, MediaType::class).nullable()

    // Timestamps
    val createdAt = datetime("created_at").clientDefault { now() }
    val updatedAt = datetime("updated_at").clientDefault { now() }

    // Soft deletion
    val deleted = bool("deleted").default(false)
    val deletedAt = datetime("deleted_at").nullable()
    val version = long("version").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, columns = arrayOf(statusId, createdAt))

        // Additional performance indexes
        index(isUnique = false, columns = arrayOf(userId, createdAt)) // For user reply activity
        index(isUnique = false, columns = arrayOf(statusId, userId)) // For user replies to status
        index(isUnique = false, columns = arrayOf(userId)) // For user-specific queries
        index(isUnique = false, columns = arrayOf(statusId)) // For status-specific queries
        index(isUnique = false, columns = arrayOf(deleted)) // For soft deletion filtering
    }
}
