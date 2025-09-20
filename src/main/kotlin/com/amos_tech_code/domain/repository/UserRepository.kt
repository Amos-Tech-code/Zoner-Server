package com.amos_tech_code.domain.repository

import com.amos_tech_code.domain.model.UserBasicInfo
import java.util.UUID

interface UserRepository {

    suspend fun getFollowedUserIds(userId: UUID): List<UUID>

    suspend fun getSuggestedUsers(userId: UUID, limit: Int): List<UUID>

    suspend fun getUsersBasicInfo(userIds: List<UUID>): List<UserBasicInfo>

    suspend fun getPopularBusinessUsers(limit: Int, excludeUserId: UUID): List<UUID>

    suspend fun getSimilarBusinessUsers(userId: UUID, limit: Int): List<UUID>

    suspend fun getRandomBusinessUsers(limit: Int, excludeUserId: UUID): List<UUID>

}
