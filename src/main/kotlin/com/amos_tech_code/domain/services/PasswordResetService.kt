package com.amos_tech_code.domain.services

import com.amos_tech_code.data.database.models.PasswordResetTokensTable
import com.amos_tech_code.data.database.models.UsersTable
import com.amos_tech_code.domain.model.ResetPasswordResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.time.LocalDateTime
import kotlin.math.pow

object PasswordResetService {

    private val OTP_EXPIRATION_MINUTES = 10
    private val OTP_LENGTH = 6

    private val random = SecureRandom()

    private fun generateOtp(): String {
        val min = 10.0.pow((OTP_LENGTH - 1).toDouble()).toInt()
        val max = 10.0.pow(OTP_LENGTH.toDouble()).toInt() - 1
        return (random.nextInt(max - min + 1) + min).toString()
    }

    fun generateAndSendResetCode(email: String): Boolean {
        return try {
            var resetCode: String? = null
            var userName: String? = null

            transaction {
                val user = UsersTable.select { UsersTable.email eq email }
                    .firstOrNull() ?: return@transaction

                val userId = user[UsersTable.id]
                userName = user[UsersTable.name] ?: "User"

                // Generate new code
                resetCode = generateOtp()
                val codeHash = BCrypt.hashpw(resetCode, BCrypt.gensalt())

                // Remove old token if exists
                PasswordResetTokensTable.deleteWhere {
                    PasswordResetTokensTable.userId eq userId
                }

                // Insert new token
                PasswordResetTokensTable.insert {
                    it[this.userId] = userId
                    it[token] = codeHash
                    it[expiresAt] = LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES.toLong())
                }
            }

            // Send email asynchronously (outside transaction)
            if (resetCode != null && userName != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        getKoin().get<EmailService>().sendPasswordResetCode(
                            email = email,
                            name = userName,
                            code = resetCode
                        )
                    } catch (e: Exception) {
                        println("Failed to send reset email to $email: ${e.message}")
                    }
                }
            }

            true
        } catch (e: Exception) {
            println("Failed to generate reset code: ${e.message}")
            false
        }
    }

    fun resendResetCode(email: String): Boolean {
        return try {
            var codeToSend: String? = null
            var userName: String? = null

            transaction {
                val user = UsersTable.select { UsersTable.email eq email }
                    .firstOrNull() ?: return@transaction

                val userId = user[UsersTable.id]
                userName = user[UsersTable.name] ?: "User"

                // Look for valid existing code
                val existing = PasswordResetTokensTable
                    .select {
                        (PasswordResetTokensTable.userId eq userId) and
                                (PasswordResetTokensTable.expiresAt greaterEq LocalDateTime.now()) and
                                (PasswordResetTokensTable.isUsed eq false)
                    }
                    .firstOrNull()

                if (existing != null) {
                    // reuse code (we don't store plain text, so regenerate new)
                    val newCode = generateOtp()
                    val codeHash = BCrypt.hashpw(newCode, BCrypt.gensalt())

                    PasswordResetTokensTable.update({ PasswordResetTokensTable.id eq existing[PasswordResetTokensTable.id] }) {
                        it[token] = codeHash
                        it[expiresAt] = LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES.toLong())
                        it[createdAt] = LocalDateTime.now()
                    }
                    codeToSend = newCode
                } else {
                    // fall back to normal flow
                    val newCode = generateOtp()
                    val codeHash = BCrypt.hashpw(newCode, BCrypt.gensalt())

                    PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.userId eq userId }
                    PasswordResetTokensTable.insert {
                        it[this.userId] = userId
                        it[token] = codeHash
                        it[expiresAt] = LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES.toLong())
                    }
                    codeToSend = newCode
                }
            }

            if (codeToSend != null && userName != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        getKoin().get<EmailService>().sendPasswordResetCode(
                            email = email,
                            name = userName!!,
                            code = codeToSend!!
                        )
                    } catch (e: Exception) {
                        println("Failed to send reset email to $email: ${e.message}")
                    }
                }
            }

            true
        } catch (e: Exception) {
            println("Failed to resend reset code: ${e.message}")
            false
        }
    }


    fun resetPasswordWithOtp(email: String, otp: String, newPassword: String): ResetPasswordResult {
        return transaction {
            // Validate inputs
            when {
                email.isBlank() -> return@transaction ResetPasswordResult.InvalidRequest
                otp.isBlank() -> return@transaction ResetPasswordResult.InvalidOtp
            }

            // Find user
            val user = UsersTable.select { UsersTable.email eq email }
                .firstOrNull()
                ?: return@transaction ResetPasswordResult.UserNotFound

            val userId = user[UsersTable.id]

            // Find valid OTP
            val resetRecord = PasswordResetTokensTable
                .select {
                    (PasswordResetTokensTable.userId eq userId) and
                            (PasswordResetTokensTable.expiresAt greaterEq LocalDateTime.now()) and
                            (PasswordResetTokensTable.isUsed eq false)
                }
                .singleOrNull()
                ?: return@transaction ResetPasswordResult.InvalidOtp

            // Verify OTP
            if (!BCrypt.checkpw(otp, resetRecord[PasswordResetTokensTable.token])) {
                return@transaction ResetPasswordResult.InvalidOtp
            }

            // Update password
            UsersTable.update({ UsersTable.id eq userId }) {
                it[passwordHash] = BCrypt.hashpw(newPassword, BCrypt.gensalt())
                it[updatedAt] = LocalDateTime.now()
            }

            // Invalidate OTP
            PasswordResetTokensTable.update({
                PasswordResetTokensTable.id eq resetRecord[PasswordResetTokensTable.id]
            }) {
                it[isUsed] = true
            }

            ResetPasswordResult.Success
        }
    }
}
