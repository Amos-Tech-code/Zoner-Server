package com.amos_tech_code.presentation.controllers

import com.amos_tech_code.presentation.dto.request.CreateBusinessProfile
import com.amos_tech_code.presentation.dto.response.ConflictException
import com.amos_tech_code.presentation.dto.response.GenericResponse
import com.amos_tech_code.domain.services.BusinessProfileService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.businessProfileRoutes() {
    route("/business") {
        post("/create") {
            try {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        GenericResponse<Unit>(success = false, message = "Unauthorized")
                    )

                val request = call.receive<CreateBusinessProfile>()

                // Validate terms acceptance
                if (!request.isTermsAccepted) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        GenericResponse<Unit>(success = false, message = "You must accept the terms and conditions")
                    )
                }

                // Create business profile
                val authResponse = BusinessProfileService.createBusinessProfile(
                    userId = UUID.fromString(userId),
                    request = request
                )

                call.respond(
                    HttpStatusCode.Created,
                    GenericResponse(
                        success = true,
                        message = "Business profile created successfully",
                        data = authResponse
                    )
                )

            } catch (e: BadRequestException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    GenericResponse<Unit>(success = false, message = "Invalid request")
                )
            } catch (e: ConflictException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    GenericResponse<Unit>(success = false, message = e.message ?: "Business profile already exists")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(success = false, message = "Failed to create business profile: ${e.message}")
                )
            }
        }
    }
}
