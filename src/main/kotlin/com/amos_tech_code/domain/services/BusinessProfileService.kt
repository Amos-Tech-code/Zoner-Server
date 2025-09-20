package com.amos_tech_code.domain.services

import com.amos_tech_code.application.configs.JwtConfig
import com.amos_tech_code.data.database.models.BusinessProfilesTable
import com.amos_tech_code.data.database.models.UsersTable
import com.amos_tech_code.domain.model.RegistrationStage
import com.amos_tech_code.domain.model.UserRole
import com.amos_tech_code.presentation.dto.request.CreateBusinessProfile
import com.amos_tech_code.presentation.dto.response.AuthResponse
import com.amos_tech_code.presentation.dto.response.BusinessProfileResponse
import com.amos_tech_code.presentation.dto.response.ConflictException
import com.amos_tech_code.presentation.dto.response.UserResponse
import io.ktor.server.plugins.BadRequestException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

object BusinessProfileService {

    fun createBusinessProfile(userId: UUID, request: CreateBusinessProfile): AuthResponse {
        return transaction {
            // Check if user exists
            val user = UsersTable.select { UsersTable.id eq userId }.singleOrNull()
                ?: throw BadRequestException("User not found")

            // Check if business profile already exists for this user
            val existingProfile = BusinessProfilesTable.select { BusinessProfilesTable.userId eq userId }.singleOrNull()
            if (existingProfile != null) {
                throw ConflictException("Business profile already exists for this user")
            }

            // Create business profile
            val businessProfileId = UUID.randomUUID()
            BusinessProfilesTable.insert {
                it[id] = businessProfileId
                it[BusinessProfilesTable.userId] = userId
                it[BusinessProfilesTable.businessName] = request.businessName
                it[BusinessProfilesTable.businessDescription] = request.description
                it[BusinessProfilesTable.businessAddress] = request.location
                it[BusinessProfilesTable.businessPhone] = request.phoneNumber
                it[BusinessProfilesTable.category] = request.category
                it[BusinessProfilesTable.country] = request.country
                it[BusinessProfilesTable.isTermsAccepted] = request.isTermsAccepted
                it[BusinessProfilesTable.createdAt] = LocalDateTime.now()
            }

            // Update user registration stage
            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.role] = UserRole.BUSINESS
                it[UsersTable.registrationStage] = RegistrationStage.BUSINESS_ADDED
                it[UsersTable.updatedAt] = LocalDateTime.now()
            }

            // Generate new token with updated claims if needed
            val token = JwtConfig.generateToken(userId.toString(), UserRole.BUSINESS)

            // Get updated user with business profile
            val updatedUser = UsersTable.select { UsersTable.id eq userId }.single()
            val businessProfile = BusinessProfilesTable
                .select { BusinessProfilesTable.userId eq userId }
                .singleOrNull()
                ?.let {
                    BusinessProfileResponse(
                        businessName = it[BusinessProfilesTable.businessName],
                        businessEmail = it[BusinessProfilesTable.businessEmail],
                        isVerified = it[BusinessProfilesTable.isVerified],
                        businessLogo = it[BusinessProfilesTable.businessLogo]
                    )
                }

            AuthResponse(
                token = token,
                user = UserResponse(
                    id = userId.toString(),
                    email = updatedUser[UsersTable.email],
                    name = updatedUser[UsersTable.name],
                    username = updatedUser[UsersTable.username],
                    profilePicUrl = updatedUser[UsersTable.profilePicUrl],
                    role = updatedUser[UsersTable.role].name,
                    registrationStage = updatedUser[UsersTable.registrationStage].name,
                    businessProfile = businessProfile
                )
            )
        }
    }
}