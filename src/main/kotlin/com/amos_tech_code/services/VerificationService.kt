package com.amos_tech_code.services

import com.amos_tech_code.database.EmailVerificationTokensTable
import com.amos_tech_code.database.UsersTable
import com.amos_tech_code.model.RegistrationStage
import com.amos_tech_code.model.request.VerificationError
import com.amos_tech_code.model.request.VerificationResult
import com.amos_tech_code.model.response.AlreadyVerifiedException
import io.ktor.server.plugins.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.*

object VerificationService {

    fun generateVerificationCode(userId: UUID): String {
        return transaction {
            // Generate random 4-digit code
            val rawCode = (1000..9999).random().toString()
            val codeHash = BCrypt.hashpw(rawCode, BCrypt.gensalt())

            // Delete any existing codes for this user
            EmailVerificationTokensTable.deleteWhere {
                EmailVerificationTokensTable.userId eq userId
            }

            // Store new code
            EmailVerificationTokensTable.insert {
                it[EmailVerificationTokensTable.userId] = userId
                it[EmailVerificationTokensTable.code] = codeHash
                it[EmailVerificationTokensTable.expiresAt] = LocalDateTime.now().plusMinutes(10)
                it[EmailVerificationTokensTable.isUsed] = false
            }

            rawCode
        }
    }

    fun verifyCode(userId: String, rawCode: String): VerificationResult {
        return transaction {
            // Find valid, unused verification record
            val verification = EmailVerificationTokensTable
                .select {
                    (EmailVerificationTokensTable.userId eq UUID.fromString(userId)) and
                            (EmailVerificationTokensTable.expiresAt greaterEq LocalDateTime.now()) and
                            (EmailVerificationTokensTable.isUsed eq false)
                }
                .singleOrNull()

            verification?.let { record ->
                // Verify the code matches the hash
                if (BCrypt.checkpw(rawCode, record[EmailVerificationTokensTable.code])) {
                    // Mark code as used
                    EmailVerificationTokensTable.update({
                        EmailVerificationTokensTable.id eq record[EmailVerificationTokensTable.id]
                    }) {
                        it[isUsed] = true
                    }

                    // Update user as verified
                    UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                        it[isEmailVerified] = true
                        it[emailVerifiedAt] = LocalDateTime.now()
                        it[registrationStage] = RegistrationStage.EMAIL_VERIFIED
                    }
                    VerificationResult.Success
                } else {
                    VerificationResult.Error(VerificationError.INVALID_CODE)
                }
            } ?: VerificationResult.Error(VerificationError.INVALID_CODE)
        }
    }

    suspend fun resendVerificationCode(userId: String): Boolean {
        // 1. Do database work in transaction
        val (email, name, newCode) = transaction {
            val user = UsersTable.select { UsersTable.id eq UUID.fromString(userId) }
                .singleOrNull()
                ?: throw NotFoundException("User not found")

            if (user[UsersTable.isEmailVerified]) {
                throw AlreadyVerifiedException("Email already verified")
            }

            Triple(
                user[UsersTable.email],
                user[UsersTable.name] ?: "User",
                generateVerificationCode(UUID.fromString(userId))
            )
        }

        // 2. Send email outside transaction
        return try {
            val emailService = getKoin().get<EmailService>()
            emailService.sendVerificationCode(
                email = email,
                name = name,
                code = newCode
            )
            true
        } catch (e: Exception) {
            println("Failed to send verification email to $email: ${e.message}")
            false
        }
    }}