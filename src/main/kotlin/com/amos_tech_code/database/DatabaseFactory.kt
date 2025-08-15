package com.amos_tech_code.database

import com.amos_tech_code.configs.AppConfig
import com.amos_tech_code.model.AuthProvider
import com.amos_tech_code.model.RegistrationStage
import com.amos_tech_code.model.UserRole
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
            maximumPoolSize = 7
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        try {
            val dataSource = HikariDataSource(config)
            Database.connect(dataSource)

            transaction {
                // Create tables if they don't exist
                SchemaUtils.createMissingTablesAndColumns(
                    UsersTable,
                    EmailVerificationTokensTable,
                    PasswordResetTokensTable,
                    BusinessProfilesTable,
                    NotificationsTable,
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
                val password = "SecurePassword123" // Change this in production!
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
                    it[profilePicUrl] = "https://example.com/profiles/zoner_biz.jpg"
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
                    it[businessLogo] = "https://example.com/logos/zoner_logo.png"
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