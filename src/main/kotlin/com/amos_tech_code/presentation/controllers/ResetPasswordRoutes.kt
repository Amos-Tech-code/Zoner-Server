package com.amos_tech_code.presentation.controllers

import com.amos_tech_code.domain.model.ResetPasswordResult
import com.amos_tech_code.presentation.dto.request.ForgotPasswordRequest
import com.amos_tech_code.presentation.dto.request.ResetPasswordRequest
import com.amos_tech_code.presentation.dto.response.GenericResponse
import com.amos_tech_code.domain.services.PasswordResetService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.resetPasswordRoutes() {
    route("/password") {

        post("/forgot") {
            val request = call.receive<ForgotPasswordRequest>()

            if (request.email.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    GenericResponse<Unit>(
                        success = false,
                        message = "Email is required"
                    )
                )
            }

            try {
                // Call service, but don't expose whether email exists
                val emailSent = PasswordResetService.generateAndSendResetCode(request.email)

                    if (emailSent) {
                        call.respond(
                            HttpStatusCode.OK,
                            GenericResponse<Unit>(
                                success = true,
                                message = "If this email exists, a reset code has been sent"
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            GenericResponse<Unit>(false, "Failed to send verification code")
                        )
                    }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(false, "Failed to process request")
                )
            }
        }

        post("/reset") {
            val request = call.receive<ResetPasswordRequest>()
            when {
                request.email.isNullOrBlank() ||
                        request.otp.isNullOrBlank() ||
                        request.newPassword.isNullOrBlank() -> {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse<Unit>(false,"All fields are required")
                    )
                }
                request.newPassword.length < 8 -> {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse<Unit>(false,"Password must be at least 8 characters")
                    )
                }
            }
            try {

                val result = PasswordResetService.resetPasswordWithOtp(
                    email = request.email,
                    otp = request.otp,
                    newPassword = request.newPassword
                )

                when (result) {
                    ResetPasswordResult.Success ->
                        call.respond(
                            HttpStatusCode.OK,
                            GenericResponse<Unit>(success = true, message = "Password reset successfully")
                        )
                    ResetPasswordResult.UserNotFound ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            GenericResponse<Unit>(success = false, message = "Account not found")
                        )
                    ResetPasswordResult.InvalidOtp ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            GenericResponse<Unit>(success = false, message = "Invalid or expired code")
                        )
                    ResetPasswordResult.InvalidRequest ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            GenericResponse<Unit>(success = false, message = "Invalid request")
                        )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(success = false, message = "Failed to reset password")
                )
            }
        }

        post("/resend-otp") {
            val request = call.receive<ForgotPasswordRequest>()

            if (request.email.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    GenericResponse<Unit>(
                        success = false,
                        message = "Email is required"
                    )
                )
            }

            try {
                // Call service, but don't expose whether email exists
                val emailSent = PasswordResetService.generateAndSendResetCode(request.email)

                if (emailSent) {
                    call.respond(
                        HttpStatusCode.OK,
                        GenericResponse<Unit>(
                            success = true,
                            message = "If this email exists, a reset code has been sent"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        GenericResponse<Unit>(false, "Failed to send verification code")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(
                        success = false,
                        message = "Failed to process request"
                    )
                )
            }
        }

    }

}