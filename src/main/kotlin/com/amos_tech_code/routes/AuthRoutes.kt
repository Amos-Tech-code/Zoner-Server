package com.amos_tech_code.routes

import com.amos_tech_code.configs.JwtConfig
import com.amos_tech_code.model.AuthProvider
import com.amos_tech_code.model.RegistrationStage
import com.amos_tech_code.model.UserRole
import com.amos_tech_code.model.request.*
import com.amos_tech_code.model.response.*
import com.amos_tech_code.services.*
import com.amos_tech_code.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import java.util.*


fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
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
                    GenericResponse<Unit>(false, "Invalid Request")
                )
            } catch (e: ConflictException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    GenericResponse<Unit>(false, "Email already registered")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(false, "Registration failed. Something went wrong on our end.")
                )
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()

                val email = request.email ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    GenericResponse<Unit>(success = false, message = "Email is required")
                )

                val password = request.password ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    GenericResponse<Unit>(success = false, message = "Password is required")
                )

                val authResponse = AuthService.login(email, password)
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        GenericResponse<Unit>(success = false, message = "Invalid credentials")
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
                    GenericResponse<Unit>(success = false, message = e.message ?: "Not authorized")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(success = false, message = "Login failed")
                )
            }
        }

        route("/oauth") {
            // OAuth Registration Endpoint
            post("/register") {
                try {
                    val params = call.receive<Map<String, String>>()
                    val provider = params["provider"]
                    val token = params["token"]

                    if (provider == null || token == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            GenericResponse<Unit>(success = false, message = "Provider and token are required")
                        )
                        return@post
                    }

                    val authProvider = when (provider.lowercase()) {
                        "google" -> AuthProvider.GOOGLE
                        "facebook" -> AuthProvider.FACEBOOK
                        else -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                GenericResponse<Unit>(success = false, message = "Unsupported provider")
                            )
                            return@post
                        }
                    }

                    val userInfo = when (provider.lowercase()) {
                        "google" -> AuthService.validateGoogleToken(token)
                        "facebook" -> AuthService.validateFacebookToken(token)
                        else -> null
                    }

                    if (userInfo == null || userInfo["email"].isNullOrEmpty()) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            GenericResponse<Unit>(success = false, message = "Invalid token or email not found")
                        )
                        return@post
                    }

                    val email = userInfo["email"]!!
                    val name = userInfo["name"] ?: "Unknown User"

                    // Check if user already exists
                    val existingUser = AuthService.getUserByEmail(email)
                    if (existingUser != null) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            GenericResponse<Unit>(
                                success = false,
                                message = "User already exists. Please use login instead."
                            )
                        )
                        return@post
                    }

                    // Register new user
                    val result = AuthService.registerOAuthUser(
                        name = name,
                        email = email,
                        provider = authProvider,
                        role = UserRole.USER
                    )

                    call.respond(
                        HttpStatusCode.Created,
                        GenericResponse(
                            success = true,
                            message = "Registration successful",
                            data = result.toPublicResponse()
                        )
                    )

                } catch (e: ConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        GenericResponse<Unit>(success = false, message = e.message ?: "Conflict occurred")
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        GenericResponse<Unit>(success = false, message = "Registration failed: ${e.message}")
                    )
                }
            }

            // OAuth Login Endpoint
            post("/login") {
                try {
                    val params = call.receive<Map<String, String>>()
                    val provider = params["provider"]
                    val token = params["token"]

                    if (provider == null || token == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            GenericResponse<Unit>(success = false, message = "Provider and token are required")
                        )
                        return@post
                    }

                    val authProvider = when (provider.lowercase()) {
                        "google" -> AuthProvider.GOOGLE
                        "facebook" -> AuthProvider.FACEBOOK
                        else -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                GenericResponse<Unit>(success = false, message = "Unsupported provider")
                            )
                            return@post
                        }
                    }

                    val userInfo = when (provider.lowercase()) {
                        "google" -> AuthService.validateGoogleToken(token)
                        "facebook" -> AuthService.validateFacebookToken(token)
                        else -> null
                    }

                    if (userInfo == null || userInfo["email"].isNullOrEmpty()) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            GenericResponse<Unit>(success = false, message = "Invalid token or email not found")
                        )
                        return@post
                    }

                    val email = userInfo["email"]!!
                    val existingUser = AuthService.getUserByEmail(email)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            GenericResponse<Unit>(success = false, message = "User not found. Please register first.")
                        )

                    if (existingUser.authProvider != authProvider) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            GenericResponse<Unit>(
                                success = false,
                                message = "This email is registered with ${existingUser.authProvider} provider"
                            )
                        )
                        return@post
                    }

                    val authResponse = AuthService.oauthLogin(existingUser.id)
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
                        GenericResponse<Unit>(success = false, message = e.message ?: "Not authorized")
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        GenericResponse<Unit>(success = false, message = "Login failed: ${e.message}")
                    )
                }
            }
        }

        post("/verify-email") {
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

        post("/resend-otp") {
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
                        GenericResponse<Unit>(false, "Failed to send verification code")
                    )
                }
            } catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, GenericResponse<Unit>(false, "Invalid Request"))
            }
            catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, GenericResponse<Unit>(false, "User not found"))
            } catch (e: AlreadyVerifiedException) {
                call.respond(HttpStatusCode.Conflict, GenericResponse<Unit>(false, "Email already verified"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, GenericResponse<Unit>(false, "Failed to resend code"))
            }
        }

        post("/check-username") {
            try {
                val request = call.receive<CheckUserName>()
                val userId = request.userId.toUUIDOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse<Unit>(false, "Invalid user ID")
                    )

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

        post("/complete-profile") {
            var uploadedImageUrl: String? = null
            var profilePicFile: PartData.FileItem? = null

            try {
                // Receive multipart form data
                val multipart = call.receiveMultipart()
                var userId: String? = null
                var username: String? = null

                // First pass: collect all parts without disposing
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "userId" -> userId = part.value
                                "username" -> username = part.value
                            }
                            part.dispose() // Safe to dispose form items immediately
                        }
                        is PartData.FileItem -> {
                            if (part.name == "profilePic") {
                                profilePicFile = part // Don't dispose yet!
                            } else {
                                part.dispose() // Dispose other file parts we don't need
                            }
                        }
                        else -> part.dispose()
                    }
                }

                // Validate required fields
                if (userId == null || username == null) {
                    throw BadRequestException("Missing required fields")
                }

                // Normalize and validate username format first
                val normalizedUsername = normalizeAndValidateUsername(username)

                // Process image if provided
                uploadedImageUrl = profilePicFile?.let { fileItem ->
                    try {
                        ImageService.uploadProfileImage(multipart = listOf(fileItem).toMultiPartData())
                    } finally {
                        fileItem.dispose() // Dispose only after we're done with the file
                    }
                }

                // Complete profile (includes final DB check)
                val result = AuthService.completeUserProfile(
                    userId = userId,
                    username = normalizedUsername,
                    profilePicUrl = uploadedImageUrl
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
                // Clean up uploaded image if username conflict occurs
                uploadedImageUrl?.let { url ->
                    try {
                        ImageService.deleteImage(url)
                    } catch (e: Exception) {
                        // Log cleanup failure but don't fail the request
                        println("Failed to cleanup image after username conflict: $e")
                    }
                }
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
                uploadedImageUrl?.let { url ->
                    try {
                        ImageService.deleteImage(url)
                    } catch (e: Exception) {
                        println("Failed to cleanup image after bad request: $e")
                    }
                }
                call.respond(HttpStatusCode.BadRequest, GenericResponse<Unit>(false, e.message ?: "Invalid request"))
            } catch (e: Exception) {
                uploadedImageUrl?.let { url ->
                    try {
                        ImageService.deleteImage(url)
                    } catch (e: Exception) {
                        println("Failed to cleanup image after error: $e")
                    }
                }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(false, "Profile completion failed")
                )
                println("Profile completion failed ${e.message}")
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