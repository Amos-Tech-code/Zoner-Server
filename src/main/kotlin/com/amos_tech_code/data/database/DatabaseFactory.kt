package com.amos_tech_code.data.database

import com.amos_tech_code.application.configs.AppConfig
import com.amos_tech_code.data.database.models.BusinessFollowersTable
import com.amos_tech_code.data.database.models.BusinessProfilesTable
import com.amos_tech_code.data.database.models.EmailVerificationTokensTable
import com.amos_tech_code.data.database.models.NotificationsTable
import com.amos_tech_code.data.database.models.PasswordResetTokensTable
import com.amos_tech_code.data.database.models.StatusLikesTable
import com.amos_tech_code.data.database.models.StatusRepliesTable
import com.amos_tech_code.data.database.models.StatusTable
import com.amos_tech_code.data.database.models.StatusViewsTable
import com.amos_tech_code.data.database.models.UsersTable
import com.amos_tech_code.domain.model.AuthProvider
import com.amos_tech_code.domain.model.RegistrationStage
import com.amos_tech_code.domain.model.UserRole
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.*

object DatabaseFactory {
    fun init() {
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl =
                "jdbc:postgresql://${AppConfig.DB_HOST}:${AppConfig.DB_PORT}/${AppConfig.DB_NAME}?sslmode=require"
            username = AppConfig.DB_USER
            password = AppConfig.DB_PASSWORD

            // Connection pool settings
            maximumPoolSize = 20
            minimumIdle = 5       // Add minimum idle connections
            // Timeouts
            idleTimeout = 60000   // 1 minute idle timeout
            maxLifetime = 1800000 // 30 minutes max lifetime
            connectionTimeout = 30000 // 30 second connection timeout
            validationTimeout = 5000  // 5 second validation timeout

            isAutoCommit = true
        }

        try {
            val dataSource = HikariDataSource(config)
            Database.connect(dataSource)

            transaction {
                // Create tables if they don't exist
                SchemaUtils.createMissingTablesAndColumns(
                    UsersTable,
                    //Tokens
                    EmailVerificationTokensTable,
                    PasswordResetTokensTable,
                    //Business
                    BusinessProfilesTable,
                    BusinessFollowersTable,
                    //Notifications
                    NotificationsTable,
                    //Status
                    StatusTable,
                    StatusViewsTable,
                    StatusLikesTable,
                    StatusRepliesTable
                )

               // updateOwnerPassword()
            }
        } catch (e: Exception) {
            println("Database initialization failed: ${e.message}")
            throw e
        }
    }
}

fun Application.migrateDatabase() {
    // This function can be used for future database migrations


}

fun Application.seedDatabase() {
    environment.monitor.subscribe(ApplicationStarted) {
        transaction {
            // Seed admin user if none exist
            if (UsersTable.selectAll().empty()) {
                println("Seeding admin user...")

                // Generate a secure password hash (use BCrypt in production)
                val password = "SecurePassword123"
                val passwordHash : String = BCrypt.hashpw(password, BCrypt.gensalt())

                // Create admin user
                val adminId = UUID.randomUUID()
                UsersTable.insert {
                    it[id] = adminId
                    it[email] = "zoner4237@gmail.com"
                    it[name] = "Zoner Business"
                    it[authProvider] = AuthProvider.EMAIL
                    it[UsersTable.passwordHash] = passwordHash
                    it[username] = "@zoner_biz"
                    it[profilePicUrl] = "https://issukbsivkkqzsghassb.supabase.co/storage/v1/object/public/zoner_bucket/system/logov1.png"
                    it[isEmailVerified] = true
                    it[emailVerifiedAt] = LocalDateTime.now()
                    it[registrationStage] = RegistrationStage.PROFILE_COMPLETED
                    it[role] = UserRole.ADMIN
                    it[createdAt] = LocalDateTime.now()
                    it[updatedAt] = LocalDateTime.now()
                    it[isActive] = true
                }

                // Create business profile for admin
                BusinessProfilesTable.insert {
                    it[userId] = adminId
                    it[businessName] = "Zoner Enterprises"
                    it[businessEmail] = "business@zoner.com"
                    it[businessPhone] = "+1234567890"
                    it[businessAddress] = "123 Business St, Nairobi"
                    it[businessDescription] = "Premium business solutions provider"
                    it[businessLogo] = "https://issukbsivkkqzsghassb.supabase.co/storage/v1/object/public/zoner_bucket/system/logov1.png"
                    it[websiteUrl] = "https://zoner.biz"
                    it[isVerified] = true
                    it[verificationRequestedAt] = LocalDateTime.now().minusDays(1)
                    it[verifiedAt] = LocalDateTime.now()
                    it[taxId] = "TAX-123456"
                }

                println("Admin user and business profile seeded successfully")
            } else {
                println("Database already contains users, skipping seeding")
            }
        }
    }
}