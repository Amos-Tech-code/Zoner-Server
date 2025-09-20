package com.amos_tech_code.presentation.controllers

import com.amos_tech_code.presentation.dto.response.NotificationResponse
import com.amos_tech_code.domain.services.NotificationService
import com.amos_tech_code.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.notificationRoutes() {
    route("/notifications") {
        get {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

            val notifications = NotificationService.getNotifications(UUID.fromString(userId))
            val unreadCount = NotificationService.getUnreadCount(UUID.fromString(userId))

            call.respond(NotificationResponse(notifications, unreadCount))
        }

        post("/{id}/read") {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

            val notificationId = call.parameters["id"] ?: return@post call.respondError(
                HttpStatusCode.BadRequest,
                "Notification ID is required"
            )

            NotificationService.markAsRead(UUID.fromString(notificationId))
            call.respond(mapOf("message" to "Notification marked as read"))
        }

    }
}
