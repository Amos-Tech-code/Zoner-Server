package com.amos_tech_code.domain.services


import com.amos_tech_code.application.configs.JwtConfig
import com.amos_tech_code.data.database.models.BusinessProfilesTable
import com.amos_tech_code.data.database.models.UsersTable
import com.amos_tech_code.domain.model.AuthProvider
import com.amos_tech_code.domain.model.RegistrationStage
import com.amos_tech_code.domain.model.User
import com.amos_tech_code.domain.model.UserRole
import com.amos_tech_code.presentation.dto.request.CompleteProfileResult
import com.amos_tech_code.presentation.dto.request.RegisterResult
import com.amos_tech_code.presentation.dto.response.AuthResponse
import com.amos_tech_code.presentation.dto.response.AuthorizationException
import com.amos_tech_code.presentation.dto.response.BusinessProfileResponse
import com.amos_tech_code.presentation.dto.response.UserResponse
import com.amos_tech_code.presentation.dto.response.UsernameConflictException
import com.amos_tech_code.utils.normalizeAndValidateUsername
import com.amos_tech_code.utils.toUUIDOrNull
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.UUID

object AuthService {

    private val httpClient = HttpClient(CIO)

    fun registerUser(
        name: String,
        email: String,
        password: String,
        role: UserRole = UserRole.USER
    ): RegisterResult {
        return transaction {
            // Check if email exists
            val existingUser = UsersTable
                .select { UsersTable.email eq email }
                .singleOrNull()

            if (existingUser != null) {
                return@transaction RegisterResult(
                    userId = existingUser[UsersTable.id].toString(),
                    currentStage = existingUser[UsersTable.registrationStage].name,
                    nextAction = when (existingUser[UsersTable.registrationStage]) {
                        RegistrationStage.EMAIL_SUBMITTED -> "verify_email"
                        RegistrationStage.EMAIL_VERIFIED -> "complete_profile"
                        RegistrationStage.PROFILE_COMPLETED -> "login"
                        else -> "contact_support"
                    },
                    verificationCode = null, // Don't expose code for existing users
                    isExistingUser = true
                )
            }

            // Proceed with new registration if email doesn't exist
            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
            val userId = UUID.randomUUID()

            UsersTable.insert {
                it[id] = userId
                it[UsersTable.name] = name
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = role
                it[UsersTable.authProvider] = AuthProvider.EMAIL
                it[UsersTable.registrationStage] = RegistrationStage.EMAIL_SUBMITTED
                it[UsersTable.isEmailVerified] = false
            }

            val verificationCode = VerificationService.generateVerificationCode(userId)

            RegisterResult(
                userId = userId.toString(),
                currentStage = RegistrationStage.EMAIL_SUBMITTED.name,
                nextAction = "verify_email",
                verificationCode = verificationCode,
                isExistingUser = false
            )
        }
    }

    fun completeUserProfile(
        userId: String,
        username: String,
        profilePicUrl: String?
    ): CompleteProfileResult = transaction {
        val userUUID = userId.toUUIDOrNull() ?: throw BadRequestException("Invalid user ID")

        val userRecord = UsersTable
            .select { UsersTable.id eq userUUID }
            .singleOrNull()
            ?: throw NotFoundException("User not found")

        if (userRecord[UsersTable.registrationStage] != RegistrationStage.EMAIL_VERIFIED) {
            throw BadRequestException("Verify email first")
        }

        val normalized = normalizeAndValidateUsername(username) // '@' + lowercase

        // Optional pre-check (nice-to-have, but DB constraint is the real guard)
        val taken = UsersTable
            .select { (UsersTable.username eq normalized) and (UsersTable.id neq userUUID) }
            .any()
        if (taken) {
            throw UsernameConflictException(normalized, userId)
        }

        try {
            UsersTable.update({ UsersTable.id eq userUUID }) {
                it[UsersTable.username] = normalized
                it[UsersTable.profilePicUrl] = profilePicUrl
                it[UsersTable.registrationStage] = RegistrationStage.PROFILE_COMPLETED
                it[UsersTable.updatedAt] = LocalDateTime.now()
            }
        } catch (e: ExposedSQLException) {
            // If the unique index on lower(username) fires:
            if (e.message?.contains("idx_users_username_lower_unique", ignoreCase = true) == true ||
                e.message?.contains("unique", ignoreCase = true) == true
            ) {
                throw UsernameConflictException(normalized, userId)
            }
            throw e
        }

        CompleteProfileResult(
            user = UserResponse(
                id = userId,
                email = userRecord[UsersTable.email],
                name = userRecord[UsersTable.name],
                username = normalized,
                profilePicUrl = profilePicUrl,
                role = userRecord[UsersTable.role].name,
                registrationStage = RegistrationStage.PROFILE_COMPLETED.name,
                businessProfile = null
            )
        )
    }

    fun login(email: String, password: String): AuthResponse? {
        return transaction {
            // 1. Find user by email
            val user = UsersTable.select {
                UsersTable.email eq email }
                .singleOrNull()
                ?: return@transaction null

            // 2. Verify password
            val storedHash = user[UsersTable.passwordHash]
            if (storedHash == null || !BCrypt.checkpw(password, storedHash)) {
                return@transaction null
            }

            // 3. Check account status
            if (!user[UsersTable.isActive] || user[UsersTable.isBanned]) {
                return@transaction null
            }

            // 4. Enforce email verification
            if (!user[UsersTable.isEmailVerified]) {
                throw AuthorizationException("Email not verified")
            }

            // 5. Get business profile if exists
            val businessProfile = BusinessProfilesTable
                .select { BusinessProfilesTable.userId eq user[UsersTable.id] }
                .singleOrNull()
                ?.let {
                    BusinessProfileResponse(
                        businessName = it[BusinessProfilesTable.businessName],
                        businessEmail = it[BusinessProfilesTable.businessEmail],
                        isVerified = it[BusinessProfilesTable.isVerified],
                        businessLogo = it[BusinessProfilesTable.businessLogo]
                    )
                }

            // 6. Update user last login
            UsersTable.update({ UsersTable.email eq email }) {
                it[UsersTable.lastLoginAt] = LocalDateTime.now()
            }

            // 7. Generate token
            val token = JwtConfig.generateToken(
                userId = user[UsersTable.id].toString(),
                role = user[UsersTable.role]
            )

            // 8. Prepare user response
            val userResponse = UserResponse(
                id = user[UsersTable.id].toString(),
                email = user[UsersTable.email],
                name = user[UsersTable.name],
                username = user[UsersTable.username],
                profilePicUrl = user[UsersTable.profilePicUrl],
                role = user[UsersTable.role].name,
                registrationStage = user[UsersTable.registrationStage].name,
                businessProfile = businessProfile
            )

            AuthResponse(token, userResponse)
        }
    }

    // Google OAuth User Info
    /**
     * Validate Google ID Token and get user information.
     */
    suspend fun validateGoogleToken(idToken: String): Map<String, String>? {
        val response: HttpResponse = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }
        val responseBody = response.bodyAsText() // Read as plain text first
        //println("Response Body: $responseBody") // Debug response

        // Parse as JsonObject
        val jsonObject: JsonObject = Json.parseToJsonElement(responseBody).jsonObject

        return if (response.status == HttpStatusCode.OK) {
            val userInfo = jsonObject
            mapOf(
                "email" to userInfo["email"]?.jsonPrimitive?.content.orEmpty(),
                "name" to userInfo["name"]?.jsonPrimitive?.content.orEmpty()
            )
        } else {
            null
        }
    }

    fun registerOAuthUser(
        name: String,
        email: String,
        provider: AuthProvider,
        role: UserRole = UserRole.USER
    ): RegisterResult {
        return transaction {
            // Check if email exists
            val existingUser = UsersTable
                .select { UsersTable.email eq email }
                .singleOrNull()

            if (existingUser != null) {
                return@transaction RegisterResult(
                    userId = existingUser[UsersTable.id].toString(),
                    currentStage = existingUser[UsersTable.registrationStage].name,
                    nextAction = when (existingUser[UsersTable.registrationStage]) {
                        RegistrationStage.EMAIL_SUBMITTED -> "verify_email"
                        RegistrationStage.EMAIL_VERIFIED -> "complete_profile"
                        RegistrationStage.PROFILE_COMPLETED -> "login"
                        else -> "contact_support"
                    },
                    verificationCode = null,
                    isExistingUser = true
                )
            }

            // Proceed with new registration
            val userId = UUID.randomUUID()

            UsersTable.insert {
                it[id] = userId
                it[UsersTable.name] = name
                it[UsersTable.email] = email
                it[UsersTable.role] = role
                it[UsersTable.authProvider] = provider
                it[UsersTable.registrationStage] = RegistrationStage.EMAIL_VERIFIED
                it[UsersTable.isEmailVerified] = true
                it[UsersTable.emailVerifiedAt] = LocalDateTime.now()
            }

            RegisterResult(
                userId = userId.toString(),
                currentStage = RegistrationStage.EMAIL_VERIFIED.name,
                nextAction = "login",
                isExistingUser = false
            )
        }
    }

    fun oauthLogin(userId: UUID): AuthResponse {
        return transaction {
            val user = UsersTable.select { UsersTable.id eq userId }.single()
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

            // Update last login
            UsersTable.update({ UsersTable.id eq userId }) {
                it[lastLoginAt] = LocalDateTime.now()
            }

            val token = JwtConfig.generateToken(userId.toString(), user[UsersTable.role])

            AuthResponse(
                token = token,
                user = UserResponse(
                    id = userId.toString(),
                    email = user[UsersTable.email],
                    name = user[UsersTable.name],
                    username = user[UsersTable.username],
                    profilePicUrl = user[UsersTable.profilePicUrl],
                    role = user[UsersTable.role].name,
                    registrationStage = user[UsersTable.registrationStage].name,
                    businessProfile = businessProfile
                )
            )
        }
    }

    /**
     * Validate Facebook Access Token and get user information.
     */
    suspend fun validateFacebookToken(accessToken: String): Map<String, String>? {
        val response: HttpResponse = httpClient.get("https://graph.facebook.com/me") {
            parameter("fields", "id,name,email")
            parameter("access_token", accessToken)
        }
        val responseBody = response.bodyAsText() // Read as plain text first
        println("Response Body: $responseBody") // Debug response

        // Parse as JsonObject
        val jsonObject: JsonObject = Json.parseToJsonElement(responseBody).jsonObject


        return if (response.status == HttpStatusCode.OK) {
            val userInfo = jsonObject
            mapOf(
                "email" to userInfo["email"]?.jsonPrimitive?.content.orEmpty(),
                "name" to userInfo["name"]?.jsonPrimitive?.content.orEmpty()
            )
        } else {
            null
        }
    }

    fun getUserByEmail(email: String): User? {
        return transaction {
            UsersTable.select { UsersTable.email eq email }
                .singleOrNull()
                ?.let { row ->
                    User(
                        id = row[UsersTable.id],
                        email = row[UsersTable.email],
                        name = row[UsersTable.name],
                        authProvider = row[UsersTable.authProvider],
                        registrationStage = row[UsersTable.registrationStage]
                    )
                }
        }
    }

    fun updateFcmToken(userId: UUID, token: String) {

        try {
            transaction {
                val updatedRows = UsersTable.update({ UsersTable.id eq userId }) {
                    it[fcmToken] = token
                }
               // println("Updated rows: $updatedRows")
            }
        } catch (e: Exception) {
            println("Update failed: ${e.message}")
            e.printStackTrace()
        }

    }

}