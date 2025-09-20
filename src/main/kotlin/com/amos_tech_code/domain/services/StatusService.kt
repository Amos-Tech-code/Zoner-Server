package com.amos_tech_code.domain.services

import com.amos_tech_code.domain.model.*
import com.amos_tech_code.domain.repository.StatusRepository
import com.amos_tech_code.domain.repository.UserRepository
import com.amos_tech_code.presentation.dto.response.*
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

object StatusService {

    private val statusRepository = getKoin().get<StatusRepository>()
    private val userRepository = getKoin().get<UserRepository>()
    private val logger = LoggerFactory.getLogger(StatusService::class.java)

    // Threshold to switch between simple pagination and recommendation algorithm
    private const val RECOMMENDATION_THRESHOLD = 30
    private const val DEFAULT_PAGE_SIZE = 10


    suspend fun uploadStatus(
        userId: UUID,
        caption: String?,
        mediaType: MediaType,
        durationMillis: Long,
        multipart: MultiPartData
    ): StatusUploadResponse {

        val (mediaUrl, blurHash) = when (mediaType) {
            MediaType.IMAGE -> {
                ImageService.uploadImageWithBlurHash(multipart, UploadFolder.STATUS.folderName)
            }

            MediaType.VIDEO -> {
                val (videoUrl, thumbnailBytes) = VideoService.processStatusVideo(multipart, UploadFolder.STATUS.folderName)
                val blurHash = ImageService.generateBlurHash(thumbnailBytes)
                Pair(videoUrl, blurHash)
            }
        }

        val expiresAt = LocalDateTime.now().plusHours(24)
        val now = LocalDateTime.now()

        val status = Status(
            id = UUID.randomUUID(),
            userId = userId,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            caption = caption,
            blurHash = blurHash,
            durationMillis = durationMillis,
            expiresAt = expiresAt,
            createdAt = now,
            updatedAt = now,
        )

        val createdStatus = statusRepository.createStatus(status)

        return createdStatus.toUploadResponseDto()
    }

    suspend fun getStatusesForUser(userId: UUID): UserStatusResponse {
        val statuses = statusRepository.getStatusesByUserId(userId)
            .map { it.toUploadResponseDto() }

        return UserStatusResponse(statuses)
    }

    suspend fun getActiveStatusesForUsers(
        currentUserId: UUID,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): StatusGroupsResponseDto = withContext(Dispatchers.IO) {
        try {
            require(page > 0) { "Page must be greater than 0" }
            require(pageSize > 0) { "Page size must be greater than 0" }

            // Execute count and data retrieval in parallel
            val totalStatusesDeferred = async { statusRepository.getTotalBusinessStatusesCount(excludeUserId = currentUserId) }
            val statusesDeferred = async {
                if (totalStatusesDeferred.await() <= RECOMMENDATION_THRESHOLD) {
                    statusRepository.getRecentBusinessStatusesPaginated(currentUserId, page, pageSize)
                } else {
                    val recommendedUserIds = getRecommendedUserIds(currentUserId)
                    if (recommendedUserIds.isEmpty()) {
                        statusRepository.getRecentBusinessStatusesPaginated(currentUserId, page, pageSize)
                    } else {
                        statusRepository.getStatusesFromUsersPaginated(recommendedUserIds, currentUserId, page, pageSize)
                    }
                }
            }

            val statuses = statusesDeferred.await()
            if (statuses.isEmpty()) {
                StatusGroupsResponseDto(emptyList(), hasMore = false, totalPages = 0, currentPage = page)
            }

            // Process in parallel
            val authorIds = statuses.map { it.userId }.distinct()
            val userDetailsDeferred = async { userRepository.getUsersBasicInfo(authorIds) }
            val viewedStatusIdsDeferred = async { statusRepository.getViewedStatusIds(currentUserId, authorIds) }

            val userDetails = userDetailsDeferred.await()
            val viewedStatusIds = viewedStatusIdsDeferred.await()

            val statusGroups = processStatusesToGroups(statuses, userDetails, viewedStatusIds)
            val totalStatuses = totalStatusesDeferred.await()
            val totalPages = (totalStatuses + pageSize - 1) / pageSize
            val hasMore = page < totalPages

            StatusGroupsResponseDto(
                statusGroups = statusGroups,
                hasMore = hasMore,
                totalPages = totalPages,
                currentPage = page
            )
        } catch (e: Exception) {
            logger.error("Failed to get recommended statuses", e)
            throw IllegalStateException("Failed to retrieve recommended statuses.")
        }

    }

    private fun processStatusesToGroups(
        statuses: List<Status>,
        userDetails: List<UserBasicInfo>,
        viewedStatusIds: Set<UUID>
    ): List<StatusGroup> {
        if (statuses.isEmpty()) return emptyList()

        val userDetailsMap = userDetails.associateBy { it.id }

        return statuses.groupBy { it.userId }
            .mapNotNull { (userId, userStatuses) ->
                val user = userDetailsMap[userId] ?: return@mapNotNull null

                val otherUserStatuses = userStatuses.map { status ->
                    OtherUserStatus(
                        id = status.id.toString(),
                        mediaUrl = status.mediaUrl,
                        caption = status.caption,
                        mediaType = status.mediaType,
                        createdAt = status.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
                        isViewed = viewedStatusIds.contains(status.id),
                        blurHash = status.blurHash,
                        durationMillis = status.durationMillis,
                        likesCount = status.likeCount,
                        viewsCount = status.viewCount,
                        repliesCount = status.replyCount,
                        expiresAt = status.expiresAt.toEpochSecond(ZoneOffset.UTC) * 1000,
                        lastUpdated = status.updatedAt.toEpochSecond(ZoneOffset.UTC) * 1000,
                        version = status.version
                    )
                }

                StatusGroup(
                    authorId = userId.toString(),
                    authorName = user.name ?: "Business User",
                    authorAvatar = user.profilePicUrl,
                    statuses = otherUserStatuses,
                    updatedAt = otherUserStatuses.maxOfOrNull { it.lastUpdated } ?: 0,
                    unviewedCount = otherUserStatuses.count { !it.isViewed }
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    private suspend fun getRecommendedUserIds(currentUserId: UUID): List<UUID> {
        // Implement your recommendation logic here with priority:
        val recommendedUsers = mutableListOf<UUID>()

        // 1. Users that current user follows (highest priority)
        val followedUsers = userRepository.getFollowedUserIds(currentUserId)
        recommendedUsers.addAll(followedUsers)

        // 2. Users with high engagement (many likes/views)
        val popularUsers = userRepository.getPopularBusinessUsers(limit = 10, excludeUserId = currentUserId)
        recommendedUsers.addAll(popularUsers)

        // 3. Users in same location/category (if you have this data)
        val similarUsers = userRepository.getSimilarBusinessUsers(currentUserId, limit = 5)
        recommendedUsers.addAll(similarUsers)

        // 4. Random active users (lowest priority, fill if needed)
        if (recommendedUsers.size < 5) {
            val randomUsers = userRepository.getRandomBusinessUsers(limit = 5 - recommendedUsers.size, excludeUserId = currentUserId)
            recommendedUsers.addAll(randomUsers)
        }

        // Remove duplicates and limit to reasonable number
        return recommendedUsers.distinct().take(20)
    }

    suspend fun recordStatusView(statusId: UUID, viewerId: UUID, viewDuration: Long = 0L): Boolean {
        return statusRepository.recordView(statusId, viewerId, viewDuration)
    }

    suspend fun likeStatus(statusId: UUID, userId: UUID): Boolean {
        return statusRepository.likeStatus(statusId, userId)
    }

    suspend fun unlikeStatus(statusId: UUID, userId: UUID): Boolean {
        return statusRepository.unlikeStatus(statusId, userId)
    }

    suspend fun addReply(
        statusId: UUID,
        userId: UUID,
        text: String,
        mediaUrl: String? = null,
        mediaType: MediaType? = null
    ): StatusReply {
        return statusRepository.addReply(statusId, userId, text, mediaUrl, mediaType)
    }

    suspend fun getReplies(statusId: UUID, page: Int = 1, pageSize: Int = 20): List<StatusReply> {
        return statusRepository.getReplies(statusId, page, pageSize)
    }

    suspend fun deleteReply(replyId: UUID, userId: UUID): Boolean {
        return statusRepository.deleteReply(replyId, userId)
    }

    suspend fun updateStatusCaption(statusId: UUID, userId: UUID, caption: String?): Boolean {
        val currentVersion = statusRepository.getStatusVersion(statusId) ?: return false
        return statusRepository.updateStatusCaption(statusId, userId, caption, currentVersion)
    }

    suspend fun deleteStatus(statusId: UUID, userId: UUID): Boolean {
        val currentVersion = statusRepository.getStatusVersion(statusId) ?: return false
        return statusRepository.softDeleteStatus(statusId, userId, currentVersion)
    }

    suspend fun cleanupExpiredStatuses() {
        statusRepository.deleteExpiredStatuses()
    }

    private fun Status.toUploadResponseDto() = StatusUploadResponse(
        id = this.id.toString(),
        mediaUrl = this.mediaUrl,
        caption = this.caption,
        mediaType = this.mediaType.name,
        blurHash = this.blurHash,
        durationMillis = this.durationMillis,
        viewCount = this.viewCount,
        likeCount = this.likeCount,
        replyCount = this.replyCount,
        createdAt = this.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
        lastUpdated = this.updatedAt.toEpochSecond(ZoneOffset.UTC) * 1000,
        expiresAt = this.expiresAt.toEpochSecond(ZoneOffset.UTC) * 1000,
        version = this.version
    )

}