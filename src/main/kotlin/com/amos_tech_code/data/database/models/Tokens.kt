package com.amos_tech_code.data.database.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime.now

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