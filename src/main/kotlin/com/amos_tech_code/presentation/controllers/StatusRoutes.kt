package com.amos_tech_code.presentation.controllers

import com.amos_tech_code.domain.model.MediaType
import com.amos_tech_code.domain.services.StatusService
import com.amos_tech_code.presentation.dto.request.StatusReactionRequest
import com.amos_tech_code.presentation.dto.response.GenericResponse
import com.amos_tech_code.utils.getUserIdFromJWT
import com.amos_tech_code.utils.respondBadRequest
import com.amos_tech_code.utils.respondForbidden
import com.amos_tech_code.utils.respondUnauthorized
import com.amos_tech_code.utils.toMultiPartData
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.statusRoutes() {
    route("/status") {
        post {
            val userId = call.getUserIdFromJWT() ?: return@post call.respondUnauthorized()
            val role = call.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
            if (role != "BUSINESS") {
                return@post call.respondForbidden(message = "Create a business profile to add status.")
            }

            try {
                val multipart = call.receiveMultipart()
                var caption: String? = null
                var mediaType: MediaType? = null
                var durationMillis: Long = 0
                var filePart: PartData.FileItem? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> when (part.name) {
                            "caption" -> caption = part.value
                            "mediaType" -> mediaType = MediaType.valueOf(part.value.uppercase())
                            "durationMillis" -> durationMillis = part.value.toLongOrNull() ?: 0L
                            else -> {
                                // Unknown form field, ignore
                            }
                        }

                        is PartData.FileItem -> filePart = part

                        else -> {
                            // Dispose unknown part to avoid leaks
                            part.dispose()
                        }
                    }
                }

                if (mediaType == null || filePart == null) {
                    return@post call.respondBadRequest("Missing required fields")
                }

                val response = StatusService.uploadStatus(
                    userId = userId,
                    caption = caption,
                    mediaType = mediaType,
                    durationMillis = durationMillis,
                    multipart = listOf(filePart).toMultiPartData()
                )

                call.respond(
                    HttpStatusCode.Created,
                    GenericResponse(success = true, data = response, message = "Status uploaded successfully")
                )

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    GenericResponse<Unit>(success = false, message = "Failed to upload status: ${e.message}")
                )
            }
        }

        get {
            val userId = call.getUserIdFromJWT() ?: return@get call.respondUnauthorized()

            val statuses = StatusService.getStatusesForUser(userId)
            call.respond(
                HttpStatusCode.OK,
                GenericResponse(success = true, data = statuses, message = "User status retrieved successfully.")
            )
        }

        get("/discover") {
            val userId = call.getUserIdFromJWT() ?: return@get call.respondUnauthorized()

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10

            if (page < 1 || pageSize < 1 || pageSize > 50) {
                return@get call.respondBadRequest(message = "Invalid pagination parameters")
            }

            val recommendedStatuses = StatusService.getActiveStatusesForUsers(userId, page, pageSize)

            call.respond(
                HttpStatusCode.OK,
                GenericResponse(
                    success = true,
                    data = recommendedStatuses,
                    message = "Recommended statuses retrieved successfully."
                )
            )
        }

        // Get statuses from specific users
        get("/users/{userId}") {
            val currentUserId = call.getUserIdFromJWT() ?: return@get call.respondUnauthorized()
            val targetUserId = call.parameters["userId"]?.let { UUID.fromString(it) }
                ?: return@get call.respondBadRequest("Invalid user ID")

            val statuses = StatusService.getStatusesForUser(targetUserId)
            call.respond(
                HttpStatusCode.OK,
                GenericResponse(success = true, data = statuses, message = "User statuses retrieved successfully.")
            )
        }

        delete {
            val statusId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@delete call.respondBadRequest(message = "Invalid status ID")

            val userId = call.getUserIdFromJWT() ?: return@delete call.respondUnauthorized()

            StatusService.deleteStatus(statusId, userId)

            call.respond(
                HttpStatusCode.Gone,
                GenericResponse<Unit>(success = true, message = "Status deleted successfully.")
            )
        }

        route("/{id}") {
            post("/view") {
                val statusId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@post call.respondBadRequest(message = "Invalid status ID")

                val userId = call.getUserIdFromJWT() ?: return@post call.respondUnauthorized()

                val viewDuration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 0L

                StatusService.recordStatusView(statusId, userId, viewDuration)

                call.respond(
                    HttpStatusCode.OK,
                    GenericResponse<Unit>(success = true, message = "View recorded")
                )
            }

            post("/like") {
                val statusId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@post call.respondBadRequest(message = "Invalid status ID")

                val userId = call.getUserIdFromJWT() ?: return@post call.respondUnauthorized()

                StatusService.likeStatus(statusId, userId)

                call.respond(
                    HttpStatusCode.OK,
                    GenericResponse<Unit>(success = true, message = "Reaction added")
                )
            }

            delete("/like") {
                val statusId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@delete call.respondBadRequest(message = "Invalid status ID")

                val userId = call.getUserIdFromJWT() ?: return@delete call.respondUnauthorized()

                StatusService.unlikeStatus(statusId, userId)

                call.respond(
                    HttpStatusCode.OK,
                    GenericResponse<Unit>(success = true, message = "Reaction removed")
                )
            }
        }
    }
}