package com.amos_tech_code.database

import com.amos_tech_code.model.AuthProvider
import com.amos_tech_code.model.RegistrationStage
import com.amos_tech_code.model.UserRole
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

object UsersTable : Table("users") {
    // Core Identity
    val id: Column<UUID> = uuid("id").autoGenerate()
    val email: Column<String> = varchar("email", 255).uniqueIndex()
    val name: Column<String?> = varchar("name", 100).nullable()

    // Authentication
    val authProvider: Column<AuthProvider> = enumerationByName("auth_provider", 20, AuthProvider::class)
        .default(AuthProvider.EMAIL)
    val passwordHash: Column<String?> = varchar("password_hash", 255).nullable()

    // Profile Data
    val username: Column<String?> = varchar("username", 30).nullable().uniqueIndex()
    val profilePicUrl: Column<String?> = text("profile_pic_url").nullable()

    // Email Verification
    val isEmailVerified: Column<Boolean> = bool("is_email_verified").default(false)
    val emailVerifiedAt: Column<LocalDateTime?> = datetime("email_verified_at").nullable()

    // Registration Progress
    val registrationStage: Column<RegistrationStage> =
        enumerationByName("registration_stage", 20, RegistrationStage::class)
            .default(RegistrationStage.EMAIL_SUBMITTED)

    // Roles
    val role: Column<UserRole> = enumerationByName("role", 20, UserRole::class)
        .default(UserRole.USER)

    // Timestamps
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }
    val lastLoginAt: Column<LocalDateTime?> = datetime("last_login_at").nullable()

    // Status Flags
    val isActive: Column<Boolean> = bool("is_active").default(true)
    val isBanned: Column<Boolean> = bool("is_banned").default(false)

    // Device Integration
    val fcmToken: Column<String?> = varchar("fcm_token", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object EmailVerificationTokensTable : Table("email_verification_tokens") {
    val id = uuid("id").autoGenerate()
    val userId = (uuid("user_id") references UsersTable.id).uniqueIndex()
    val code = varchar("code", 255)  // Stores 4-digit code
    val expiresAt = datetime("expires_at")  // Typically 10 minutes expiration
    val createdAt = datetime("created_at").clientDefault { now() }
    val isUsed = bool("is_used").default(false)

    override val primaryKey = PrimaryKey(id)
}

object PasswordResetTokensTable : Table("password_reset_tokens") {
    val id = uuid("id").autoGenerate()
    val userId = (uuid("user_id") references UsersTable.id).uniqueIndex()
    val token = varchar("token", 255).uniqueIndex()
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at").clientDefault { now() }
    val isUsed = bool("is_used").default(false)

    override val primaryKey = PrimaryKey(id)

}

object BusinessProfilesTable : Table("business_profiles") {
    val id = uuid("id").autoGenerate()
    val userId = (uuid("user_id") references UsersTable.id).uniqueIndex()
    val businessName = varchar("business_name", 255)
    val businessEmail = varchar("business_email", 255).nullable()
    val businessPhone = varchar("business_phone", 50).nullable()
    val businessAddress = text("business_address").nullable()
    val businessDescription = text("business_description").nullable()
    val businessLogo = text("business_logo").nullable()
    val websiteUrl = text("website_url").nullable()
    val isVerified = bool("is_verified").default(false)
    val verificationRequestedAt = datetime("verification_requested_at").nullable()
    val verifiedAt = datetime("verified_at").nullable()
    val taxId = varchar("tax_id", 100).nullable() // for verification
    val updatedAt = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)
}

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