package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.models.StatusLikesTable
import com.amos_tech_code.data.database.models.StatusRepliesTable
import com.amos_tech_code.data.database.models.StatusTable
import com.amos_tech_code.data.database.models.StatusViewsTable
import com.amos_tech_code.data.database.models.UsersTable
import com.amos_tech_code.domain.model.MediaType
import com.amos_tech_code.domain.model.Status
import com.amos_tech_code.domain.model.StatusReply
import com.amos_tech_code.domain.model.UserRole
import com.amos_tech_code.domain.repository.StatusRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

class StatusRepositoryImpl : StatusRepository {

    override suspend fun createStatus(status: Status): Status = transaction {
        StatusTable.insert {
            it[id] = status.id
            it[userId] = status.userId
            it[mediaUrl] = status.mediaUrl
            it[mediaType] = status.mediaType
            it[caption] = status.caption
            it[blurHash] = status.blurHash
            it[durationMillis] = status.durationMillis
            it[expiresAt] = status.expiresAt
            it[createdAt] = status.createdAt
            it[updatedAt] = status.updatedAt
            it[deleted] = status.deleted
            it[deletedAt] = status.deletedAt
        }.resultedValues?.first()?.toStatus() ?: throw IllegalStateException("Failed to create status")
    }

    override suspend fun getStatusById(id: UUID): Status? = transaction {
        StatusTable
            .select { StatusTable.id eq id and (StatusTable.deleted eq false) }
            .firstOrNull()
            ?.toStatus()
    }

    override suspend fun getStatusesByUserId(userId: UUID, limit: Int): List<Status> = transaction {
        val now = LocalDateTime.now()
        StatusTable
            .select {
                StatusTable.userId eq userId and
                        (StatusTable.deleted eq false) and
                        (StatusTable.expiresAt greater now)
            }
            .orderBy(StatusTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toStatus() }
    }

    override suspend fun getActiveStatuses(userIds: List<UUID>): Map<UUID, List<Status>> = transaction {
        val now = LocalDateTime.now()
        StatusTable
            .select {
                StatusTable.userId inList userIds and
                        (StatusTable.deleted eq false) and
                        (StatusTable.expiresAt greater now)
            }
            .orderBy(StatusTable.createdAt to SortOrder.DESC)
            .map { it.toStatus() }
            .groupBy { it.userId }
    }

    override suspend fun getStatusesFromUsersPaginated(
        userIds: List<UUID>,
        excludeUserId: UUID,
        page: Int,
        pageSize: Int
    ): List<Status> = transaction {
        val now = LocalDateTime.now()
        val offset = (page - 1) * pageSize

        // If no user IDs provided, return empty list
        if (userIds.isEmpty()) {
            return@transaction emptyList()
        }

        StatusTable
            .join(UsersTable, JoinType.INNER, onColumn = StatusTable.userId, otherColumn = UsersTable.id)
            .select {
                (StatusTable.userId inList userIds) and
                        (StatusTable.userId neq excludeUserId) and
                        (StatusTable.deleted eq false) and
                        (StatusTable.expiresAt greater now)
            }
            .orderBy(StatusTable.createdAt to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toStatus() }
    }

    override suspend fun getRecentBusinessStatusesPaginated(
        excludeUserId: UUID,
        page: Int,
        pageSize: Int
    ): List<Status> = transaction {
        val now = LocalDateTime.now()
        val offset = (page - 1) * pageSize

        StatusTable
            .join(UsersTable, JoinType.INNER, onColumn = StatusTable.userId, otherColumn = UsersTable.id)
            .select {
                (UsersTable.role eq UserRole.BUSINESS) and
                        (StatusTable.userId neq excludeUserId) and
                        (StatusTable.deleted eq false) and
                        (StatusTable.expiresAt greater now)
            }
            .orderBy(StatusTable.createdAt to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toStatus() }
    }

    override suspend fun getTotalBusinessStatusesCount(excludeUserId: UUID): Int = transaction {
        val now = LocalDateTime.now()

        StatusTable
            .join(UsersTable, JoinType.INNER, onColumn = StatusTable.userId, otherColumn = UsersTable.id)
            .slice(StatusTable.id.count())
            .select {
                (UsersTable.role eq UserRole.BUSINESS) and
                        (StatusTable.userId neq excludeUserId) and
                        (StatusTable.deleted eq false) and
                        (StatusTable.expiresAt greater now)
            }
            .firstOrNull()
            ?.get(StatusTable.id.count())?.toInt() ?: 0
    }

    override suspend fun getViewedStatusIds(viewerId: UUID, authorIds: List<UUID>): Set<UUID> = transaction {
        if (authorIds.isEmpty()) return@transaction emptySet()
        StatusViewsTable
            .join(StatusTable, JoinType.INNER, onColumn = StatusViewsTable.statusId, otherColumn = StatusTable.id)
            .slice(StatusViewsTable.statusId)
            .select {
                StatusViewsTable.viewerId eq viewerId and
                        (StatusTable.userId inList authorIds)
            }
            .map { it[StatusViewsTable.statusId] }
            .toSet()
    }

    override suspend fun recordView(statusId: UUID, viewerId: UUID, viewDuration: Long): Boolean = transaction {
        // Check if view already exists
        val existingView = StatusViewsTable
            .select {
                StatusViewsTable.statusId eq statusId and
                        (StatusViewsTable.viewerId eq viewerId)
            }
            .firstOrNull()

        if (existingView == null) {
            // Insert new view record
            StatusViewsTable.insert {
                it[this.statusId] = statusId
                it[this.viewerId] = viewerId
                it[viewDurationMillis] = viewDuration
                it[viewedAt] = LocalDateTime.now()
            }

            // Increment view count
            StatusTable.update({ StatusTable.id eq statusId }) {
                with(SqlExpressionBuilder) {
                    it.update(StatusTable.viewCount, StatusTable.viewCount + 1)
                    it[StatusTable.updatedAt] = LocalDateTime.now()
                    it[StatusTable.version] = StatusTable.version + 1
                }
            }
            true
        } else {
            // Update existing view duration
            StatusViewsTable.update({
                StatusViewsTable.statusId eq statusId and
                        (StatusViewsTable.viewerId eq viewerId)
            }) {
                it[viewDurationMillis] = viewDuration
                it[viewedAt] = LocalDateTime.now()
            }
            false
        }
    }

    override suspend fun likeStatus(statusId: UUID, userId: UUID): Boolean = transaction {
        try {
            StatusLikesTable.insert {
                it[this.statusId] = statusId
                it[this.userId] = userId
                it[createdAt] = LocalDateTime.now()
            }

            // Update like count
            StatusTable.update({ StatusTable.id eq statusId }) {
                with(SqlExpressionBuilder) {
                    it.update(StatusTable.likeCount, StatusTable.likeCount + 1)
                    it[StatusTable.updatedAt] = LocalDateTime.now()
                    it[StatusTable.version] = StatusTable.version + 1
                }
            }
            true
        } catch (e: Exception) {
            false // Like already exists
        }
    }

    override suspend fun unlikeStatus(statusId: UUID, userId: UUID): Boolean = transaction {
        val deleted = StatusLikesTable.deleteWhere {
            StatusLikesTable.statusId eq statusId and
                    (StatusLikesTable.userId eq userId)
        } > 0

        if (deleted) {
            // Update like count
            StatusTable.update({ StatusTable.id eq statusId }) {
                with(SqlExpressionBuilder) {
                    it.update(StatusTable.likeCount, StatusTable.likeCount - 1)
                    it[StatusTable.updatedAt] = LocalDateTime.now()
                    it[StatusTable.version] = StatusTable.version + 1
                }
            }
        }
        deleted
    }

    override suspend fun addReply(statusId: UUID, userId: UUID, text: String, mediaUrl: String?, mediaType: MediaType?): StatusReply = transaction {
        val replyId = UUID.randomUUID()

        StatusRepliesTable.insert {
            it[id] = replyId
            it[this.statusId] = statusId
            it[this.userId] = userId
            it[this.text] = text
            it[this.mediaUrl] = mediaUrl
            it[this.mediaType] = mediaType
            it[createdAt] = LocalDateTime.now()
            it[updatedAt] = LocalDateTime.now()
        }

        // Update reply count
        StatusTable.update({ StatusTable.id eq statusId }) {
            with(SqlExpressionBuilder) {
                it.update(StatusTable.replyCount, StatusTable.replyCount + 1)
                it[StatusTable.updatedAt] = LocalDateTime.now()
                it[StatusTable.version] = StatusTable.version + 1
            }
        }

        getReplyById(replyId) ?: throw IllegalStateException("Failed to create reply")
    }

    override suspend fun getReplies(statusId: UUID, page: Int, pageSize: Int): List<StatusReply> = transaction {
        StatusRepliesTable
            .select {
                StatusRepliesTable.statusId eq statusId and
                        (StatusRepliesTable.deleted eq false)
            }
            .orderBy(StatusRepliesTable.createdAt to SortOrder.DESC)
            .limit(pageSize, ((page - 1) * pageSize).toLong())
            .map { it.toStatusReply() }
    }

    override suspend fun deleteReply(replyId: UUID, userId: UUID): Boolean = transaction {
        StatusRepliesTable.update({
            StatusRepliesTable.id eq replyId and
                    (StatusRepliesTable.userId eq userId) and
                    (StatusRepliesTable.deleted eq false)
        }) {
            it[StatusRepliesTable.deleted] = true
            it[StatusRepliesTable.deletedAt] = LocalDateTime.now()
            it[StatusRepliesTable.updatedAt] = LocalDateTime.now()
            it[StatusRepliesTable.version] = StatusRepliesTable.version + 1
        } > 0
    }

    override suspend fun deleteExpiredStatuses(): Int = transaction {
        val now = LocalDateTime.now()
        StatusTable.deleteWhere {
            StatusTable.expiresAt less now and
                    (StatusTable.deleted eq false)
        }
    }

    override suspend fun getStatusVersion(statusId: UUID): Long? = transaction {
        StatusTable
            .slice(StatusTable.version)
            .select { StatusTable.id eq statusId }
            .firstOrNull()
            ?.get(StatusTable.version)
    }

    override suspend fun updateStatusCaption(
        statusId: UUID,
        userId: UUID,
        caption: String?,
        expectedVersion: Long
    ): Boolean = transaction {
        val updatedRows = StatusTable.update({
            StatusTable.id eq statusId and
                    (StatusTable.userId eq userId) and
                    (StatusTable.version eq expectedVersion) and
                    (StatusTable.deleted eq false)
        }) {
            it[StatusTable.caption] = caption
            it[StatusTable.version] = expectedVersion + 1
            it[StatusTable.updatedAt] = LocalDateTime.now()
        }

        updatedRows > 0
    }

    override suspend fun softDeleteStatus(
        statusId: UUID,
        userId: UUID,
        expectedVersion: Long
    ): Boolean = transaction {
        val updatedRows = StatusTable.update({
            StatusTable.id eq statusId and
                    (StatusTable.userId eq userId) and
                    (StatusTable.version eq expectedVersion) and
                    (StatusTable.deleted eq false)
        }) {
            it[StatusTable.deleted] = true
            it[StatusTable.deletedAt] = LocalDateTime.now()
            it[StatusTable.version] = expectedVersion + 1
            it[StatusTable.updatedAt] = LocalDateTime.now()
        }

        updatedRows > 0
    }

    // Helper methods
    private fun getReplyById(replyId: UUID): StatusReply? = transaction {
        StatusRepliesTable
            .select { StatusRepliesTable.id eq replyId }
            .firstOrNull()
            ?.toStatusReply()
    }

    private fun ResultRow.toStatus() = Status(
        id = this[StatusTable.id],
        userId = this[StatusTable.userId],
        mediaUrl = this[StatusTable.mediaUrl],
        mediaType = this[StatusTable.mediaType],
        caption = this[StatusTable.caption],
        blurHash = this[StatusTable.blurHash],
        durationMillis = this[StatusTable.durationMillis],
        viewCount = this[StatusTable.viewCount],
        replyCount = this[StatusTable.replyCount],
        likeCount = this[StatusTable.likeCount],
        expiresAt = this[StatusTable.expiresAt],
        createdAt = this[StatusTable.createdAt],
        updatedAt = this[StatusTable.updatedAt],
        deleted = this[StatusTable.deleted],
        deletedAt = this[StatusTable.deletedAt],
        version = this[StatusTable.version]
    )

    private fun ResultRow.toStatusReply() = StatusReply(
        id = this[StatusRepliesTable.id],
        statusId = this[StatusRepliesTable.statusId],
        userId = this[StatusRepliesTable.userId],
        text = this[StatusRepliesTable.text],
        mediaUrl = this[StatusRepliesTable.mediaUrl],
        mediaType = this[StatusRepliesTable.mediaType],
        createdAt = this[StatusRepliesTable.createdAt],
        updatedAt = this[StatusRepliesTable.updatedAt],
        deleted = this[StatusRepliesTable.deleted],
        deletedAt = this[StatusRepliesTable.deletedAt],
        version = this[StatusRepliesTable.version]
    )
}