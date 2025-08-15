package com.amos_tech_code.routes

import com.amos_tech_code.configs.JwtConfig
import com.amos_tech_code.model.AuthProvider
import com.amos_tech_code.model.RegistrationStage
import com.amos_tech_code.model.UserRole
import com.amos_tech_code.model.request.*
import com.amos_tech_code.model.response.AlreadyVerifiedException
import com.amos_tech_code.model.response.AuthResponse
import com.amos_tech_code.model.response.AuthorizationException
import com.amos_tech_code.model.response.ConflictException
import com.amos_tech_code.model.response.GenericResponse
import com.amos_tech_code.model.response.UsernameConflictException
import com.amos_tech_code.services.AuthService
import com.amos_tech_code.services.EmailService
import com.amos_tech_code.services.UsernameService
import com.amos_tech_code.services.VerificationService
import com.amos_tech_code.utils.extractClean
import com.amos_tech_code.utils.normalizeAndValidateUsername
import com.amos_tech_code.utils.respondError
import com.amos_tech_code.utils.toUUIDOrNull
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.getKoin
import java.util.*


data class TokenRequest(val token: String)

fun Route.authRoutes() {


    post("/auth/register") {
        try {
            val request = call.receive<RegisterRequest>()
            validateRegistrationRequest(request)

            val result = AuthService.registerUser(
                name = request.name!!,
                email = request.email!!,
                password = request.password!!,
                role = request.role?.toUserRole() ?: UserRole.USER
            )

            if (!result.isExistingUser) {
                // Only send email for new registrations
                launch {
                    try {
                        getKoin().get<EmailService>().sendVerificationCode(
                            email = request.email,
                            name = request.name,
                            code = result.verificationCode!!
                        )
                    } catch (e: Exception) {
                        println("Failed to send verification email to ${request.email}: ${e.message}")
                    }
                }
            }

            call.respond(
                if (result.isExistingUser) HttpStatusCode.OK else HttpStatusCode.Created,
                GenericResponse(
                    success = true,
                    message = if (result.isExistingUser) {
                        "Account already exists. Please ${result.nextAction.replace("_", " ")}"
                    } else {
                        "Registration successful. Please check your email for verification."
                    },
                    data = result.toPublicResponse()
                )
            )

        } catch (e: BadRequestException) {
            call.respond(
                HttpStatusCode.BadRequest,
                GenericResponse<Unit>(false, e.message ?: "Invalid registration data")
            )
        } catch (e: ConflictException) {
            call.respond(
                HttpStatusCode.Conflict,
                GenericResponse<Unit>(false, "Email already registered")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                GenericResponse<Unit>(false, "Registration failed: ${e.message}")
            )
        }
    }

    post("/auth/login") {
        try {
            val request = call.receive<LoginRequest>()

            val email = request.email ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                GenericResponse(success = false, message = "Email is required", data = null)
            )

            val password = request.password ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                GenericResponse(success = false, message = "Password is required", data = null)
            )

            val authResponse = AuthService.login(email, password)
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    GenericResponse(success = false, message = "Invalid credentials", data = null)
                )

            call.respond(
                HttpStatusCode.OK,
                GenericResponse(
                    success = true,
                    message = "Login successful",
                    data = authResponse
                )
            )
        } catch (e: AuthorizationException) {
            call.respond(
                HttpStatusCode.Forbidden,
                GenericResponse(success = false, message = e.message ?: "Not authorized", data = null)
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                GenericResponse(success = false, message = "Login failed", data = null)
            )
        }
    }

    route("/auth/oauth") {
        post {
            val params = call.receive<Map<String, String>>()
            val provider = params["provider"]
            val token = params["token"]
            val type: String = params["type"] ?: "user"

            if (provider == null || token == null) {
                call.respondError("Invalid request", status = HttpStatusCode.BadRequest)
                return@post
            }
            val authProvider = when (provider.lowercase()) {
                "google" -> AuthProvider.GOOGLE
                "facebook" -> AuthProvider.FACEBOOK
                else -> AuthProvider.EMAIL
            }

            val userInfo = runBlocking {
                when (provider.lowercase()) {
                    "google" -> AuthService.validateGoogleToken(token)
                    "facebook" -> AuthService.validateFacebookToken(token)
                    else -> null
                }
            }

            if (userInfo != null) {
                val email = userInfo["email"] ?: return@post call.respondText(
                    "Email not found",
                    status = HttpStatusCode.BadRequest
                )
                val name = userInfo["name"] ?: "Unknown User"
                val jwt = AuthService.oauthLoginOrRegister(email, name, authProvider, UserRole.USER)
                call.respond(mapOf("token" to jwt))
            } else {
                call.respondError("Invalid token", status = HttpStatusCode.Unauthorized)
            }
        }
    }

    post("/auth/verify-email") {
        val request = call.receive<VerifyEmailRequest>()

        val result = VerificationService.verifyCode(request.userId, request.code)

        when (result) {
            is VerificationResult.Success -> {
                call.respond(
                    HttpStatusCode.OK,
                    GenericResponse(
                        success = true,
                        message = "Email verified successfully",
                        data = mapOf(
                            "userId" to request.userId,
                            "currentStage" to RegistrationStage.EMAIL_VERIFIED.name,
                            "nextAction" to "complete_profile"
                        )
                    )
                )
            }
            is VerificationResult.Error -> {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    GenericResponse<Unit>(
                        success = false,
                        message = when (result.reason) {
                            VerificationError.INVALID_CODE -> "Invalid verification code"
                            VerificationError.EXPIRED_CODE -> "Code has already expired"
                            else -> "Verification failed"
                        }
                    )
                )
            }

        }
    }

    post("/auth/resend-otp") {
        try {
            val request = call.receive<ResendOtpRequest>()

            val emailSent = VerificationService.resendVerificationCode(request.userId)

            if (emailSent) {
                call.respond(
                    HttpStatusCode.OK,
                    GenericResponse(
                        success = true,
                        message = "New verification code sent to your email",
                        data = mapOf(
                            "userId" to request.userId,
                            "currentStage" to RegistrationStage.EMAIL_SUBMITTED.name
                        )
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(false,"Failed to send verification code")
                )
            }
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.NotFound, GenericResponse<Unit>(false,"User not found"))
        } catch (e: AlreadyVerifiedException) {
            call.respond(HttpStatusCode.Conflict, GenericResponse<Unit>(false,"Email already verified"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, GenericResponse<Unit>(false,"Failed to resend code"))
        }
    }

    post("/auth/check-username") {
        try {
            val request = call.receive<CheckUserName>()
            val userId = request.userId.toUUIDOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, GenericResponse<Unit>(false, "Invalid user ID"))

            val clean = extractClean(request.username)
            // Will throw if invalid
            val normalized = normalizeAndValidateUsername(clean)

            val available = UsernameService.isUsernameAvailable(normalized)

            val suggestions = if (!available) {
                UsernameService.generateSuggestions(clean, userId).take(3)
            } else emptyList()

            call.respond(
                HttpStatusCode.OK,
                GenericResponse(
                    success = true,
                    message = "Username availability checked",
                    data = UsernameAvailabilityResponse(
                        available = available,
                        suggestions = suggestions
                    )
                )
            )
        } catch (e: BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, GenericResponse<Unit>(false, e.message ?: "Invalid username"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, GenericResponse<Unit>(false, "Username check failed"))
        }
    }

    post("/auth/complete-profile") {
        try {
            val request = call.receive<CompleteProfileRequest>()
            // validate up-front: throws if bad
            normalizeAndValidateUsername(request.username)

            val result = AuthService.completeUserProfile(
                userId = request.userId,
                username = request.username,
                profilePicUrl = request.profilePicUrl
            )

            val token = JwtConfig.generateToken(userId = result.user.id)

            call.respond(
                HttpStatusCode.OK,
                GenericResponse(
                    success = true,
                    message = "Profile completed successfully",
                    data = AuthResponse(token = token, user = result.user)
                )
            )
        } catch (e: UsernameConflictException) {
            val suggestions = UsernameService.generateSuggestions(
                requestedUsername = extractClean(e.requestedUsername),
                userId = e.userId.toUUIDOrNull()
            )

            call.respond(
                HttpStatusCode.Conflict,
                GenericResponse(
                    success = false,
                    message = "Username unavailable",
                    data = UsernameSuggestionResponse(
                        suggestions = suggestions
                    )
                )
            )
        } catch (e: BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, GenericResponse<Unit>(false, e.message ?: "Invalid request"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, GenericResponse<Unit>(false, "Profile completion failed"))
        }
    }

    put("/fcm-token") {
        val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
            ?: return@put call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

        val request = call.receive<FCMTokenRequest>()
        val token = request.token ?: return@put call.respondError(
            HttpStatusCode.BadRequest,
            "FCM token is required"
        )

        AuthService.updateFcmToken(UUID.fromString(userId), token)
        call.respond(mapOf("message" to "FCM token updated successfully"))
    }

}

private fun isValidEmail(email: String): Boolean {
    val emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$".toRegex()
    return emailRegex.matches(email)
}

// Extension functions for cleaner code
private fun String?.toUserRole(): UserRole = when (this?.lowercase()) {
    "admin" -> UserRole.ADMIN
    else -> UserRole.USER
}

private fun validateRegistrationRequest(request: RegisterRequest) {
    when {
        request.name.isNullOrBlank() -> throw BadRequestException("Name required")
        request.email.isNullOrBlank() -> throw BadRequestException("Email required")
        request.password.isNullOrBlank() -> throw BadRequestException("Password required")
        !isValidEmail(request.email) -> throw BadRequestException("Invalid email")
        request.password.length < 8 -> throw BadRequestException("Password too short")
    }
}