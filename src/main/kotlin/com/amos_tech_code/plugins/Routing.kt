package com.amos_tech_code.plugins

import com.amos_tech_code.routes.authRoutes
import com.amos_tech_code.routes.imageRoutes
import com.amos_tech_code.routes.notificationRoutes
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Welcome to zoner!")
        }

        authRoutes()
        imageRoutes()
        // Authenticate
        authenticate {
            notificationRoutes()
        }
    }
}
