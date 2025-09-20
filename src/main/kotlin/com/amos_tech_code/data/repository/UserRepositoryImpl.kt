package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.models.BusinessFollowersTable
import com.amos_tech_code.data.database.models.StatusTable
import com.amos_tech_code.data.database.models.UsersTable
import com.amos_tech_code.domain.model.UserBasicInfo
import com.amos_tech_code.domain.model.UserRole
import com.amos_tech_code.domain.repository.UserRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class UserRepositoryImpl : UserRepository {

    override suspend fun getFollowedUserIds(userId: UUID): List<UUID> = transaction {
        // Assuming you have a followers table
        BusinessFollowersTable
            .slice(BusinessFollowersTable.followedUserId)
            .select { BusinessFollowersTable.followerId eq userId }
            .map { it[BusinessFollowersTable.followedUserId] }
    }

    override suspend fun getSuggestedUsers(userId: UUID, limit: Int): List<UUID> = transaction {
        // Simple implementation - returns random active users excluding current user
        UsersTable
            .slice(UsersTable.id)
            .select {
                UsersTable.id neq userId and
                        (UsersTable.isActive eq true)
            }
            .limit(limit)
            .map { it[UsersTable.id] }
            .shuffled()
    }

    override suspend fun getUsersBasicInfo(userIds: List<UUID>): List<UserBasicInfo> = transaction {
        if (userIds.isEmpty()) return@transaction emptyList()
        UsersTable
            .select { UsersTable.id inList userIds }
            .map {
                UserBasicInfo(
                    id = it[UsersTable.id],
                    name = it[UsersTable.name],
                    profilePicUrl = it[UsersTable.profilePicUrl]
                )
            }
    }

    override suspend fun getPopularBusinessUsers(limit: Int, excludeUserId: UUID): List<UUID> = transaction {
        UsersTable
            .join(StatusTable, JoinType.INNER, onColumn = UsersTable.id, otherColumn = StatusTable.userId)
            .slice(UsersTable.id, StatusTable.id.count())
            .select {
                (UsersTable.role eq UserRole.BUSINESS) and
                        (UsersTable.id neq excludeUserId) and
                        (StatusTable.deleted eq false)
            }
            .groupBy(UsersTable.id)
            .orderBy(StatusTable.id.count() to SortOrder.DESC)
            .limit(limit)
            .map { it[UsersTable.id] }
    }

    override suspend fun getSimilarBusinessUsers(userId: UUID, limit: Int): List<UUID> = transaction {
        // Placeholder: Users in same category/location
        // For now, return random business users
        UsersTable
            .slice(UsersTable.id)
            .select {
                (UsersTable.role eq UserRole.BUSINESS) and
                        (UsersTable.id neq userId) and
                        (UsersTable.isActive eq true)
            }
            .limit(limit)
            .map { it[UsersTable.id] }
            .shuffled()
    }

    override suspend fun getRandomBusinessUsers(limit: Int, excludeUserId: UUID): List<UUID> = transaction {
        UsersTable
            .slice(UsersTable.id)
            .select {
                (UsersTable.role eq UserRole.BUSINESS) and
                        (UsersTable.id neq excludeUserId) and
                        (UsersTable.isActive eq true)
            }
            .limit(limit)
            .map { it[UsersTable.id] }
            .shuffled()
    }
}