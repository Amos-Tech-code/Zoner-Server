package com.amos_tech_code.domain.repository

import com.amos_tech_code.domain.model.MediaType
import com.amos_tech_code.domain.model.Status
import com.amos_tech_code.domain.model.StatusReply
import java.util.UUID

interface StatusRepository {

    suspend fun createStatus(status: Status): Status

    suspend fun getStatusById(id: UUID): Status?

    suspend fun getStatusesByUserId(userId: UUID, limit: Int = 50): List<Status>

    suspend fun getActiveStatuses(userIds: List<UUID>): Map<UUID, List<Status>>

    suspend fun getStatusesFromUsersPaginated(
        userIds: List<UUID>,
        excludeUserId: UUID,
        page: Int,
        pageSize: Int
    ): List<Status>

    suspend fun getRecentBusinessStatusesPaginated(
        excludeUserId: UUID,
        page: Int,
        pageSize: Int
    ): List<Status>

    suspend fun getTotalBusinessStatusesCount(excludeUserId: UUID): Int

    suspend fun getViewedStatusIds(viewerId: UUID, authorIds: List<UUID>): Set<UUID>

    suspend fun recordView(statusId: UUID, viewerId: UUID, viewDuration: Long = 0L): Boolean

    suspend fun likeStatus(statusId: UUID, userId: UUID): Boolean

    suspend fun unlikeStatus(statusId: UUID, userId: UUID): Boolean

    suspend fun addReply(statusId: UUID, userId: UUID, text: String, mediaUrl: String?, mediaType: MediaType?): StatusReply

    suspend fun getReplies(statusId: UUID, page: Int, pageSize: Int): List<StatusReply>

    suspend fun deleteReply(replyId: UUID, userId: UUID): Boolean

    suspend fun deleteExpiredStatuses(): Int

    suspend fun getStatusVersion(statusId: UUID): Long?

    suspend fun updateStatusCaption(statusId: UUID, userId: UUID, caption: String?, expectedVersion: Long): Boolean

    suspend fun softDeleteStatus(statusId: UUID, userId: UUID, expectedVersion: Long): Boolean
}