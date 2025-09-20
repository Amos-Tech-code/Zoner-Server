package com.amos_tech_code.utils

import com.amos_tech_code.presentation.dto.response.ErrorResponse
import com.amos_tech_code.presentation.dto.response.GenericResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.*
import java.util.UUID

// Extension functions for cleaner code

suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
    respond(status, ErrorResponse(status.value, message))
}

suspend fun ApplicationCall.respondUnauthorized() {
    respond(HttpStatusCode.Unauthorized, GenericResponse<Unit>(success = false, message = "Unauthorized"))
}

suspend fun ApplicationCall.respondBadRequest(message: String) {
    respond(HttpStatusCode.BadRequest, GenericResponse<Unit>(success = false, message = message))
}

suspend fun ApplicationCall.respondForbidden(message: String? = null) {
    respond(HttpStatusCode.Forbidden, GenericResponse<Unit>(success = false, message = message ?: "You do not have permission to access this resource."))
}

suspend fun ApplicationCall.getUserIdFromJWT(): UUID? {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()?.let { UUID.fromString(it) }
}