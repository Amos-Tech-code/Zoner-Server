package com.amos_tech_code.presentation.controllers

import com.amos_tech_code.domain.services.ImageService
import com.amos_tech_code.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.imageRoutes() {
    route("/images") {
        authenticate {
            post("/upload") {
                try {

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.getClaim("userId", String::class)
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "User ID not found in token")
                        )

                    val type = call.request.queryParameters["type"]?.lowercase()
                        ?: return@post call.respondError(HttpStatusCode.BadRequest, "Missing type parameter")

                    // Determine folder based on kind of media
                    val folder = when (type) {
                        "user" -> "users"
                        "admin" -> "admins"
                        "system" -> "system"
                        "status" -> "status"
                        "post" -> "posts"
                        else -> "others"
                    }

                    // Handle multipart data
                    val multipart = call.receiveMultipart()
                    val imageUrl = ImageService.uploadImage(multipart, folder)

                    call.respond(hashMapOf("url" to imageUrl))
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        e.message ?: "Error uploading image"
                    )
                }
            }

            delete("/{imageUrl}") {
                try {
                    val imageUrl = call.parameters["imageUrl"] 
                        ?: return@delete call.respondError(HttpStatusCode.BadRequest, "Image URL required")
                    
                    ImageService.deleteImage(imageUrl)
                    call.respond(hashMapOf("message" to "Image deleted successfully"))
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        e.message ?: "Error deleting image"
                    )
                }
            }
        }
    }
} 