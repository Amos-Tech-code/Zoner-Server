package com.amos_tech_code.application.plugins

import com.amos_tech_code.presentation.controllers.authRoutes
import com.amos_tech_code.presentation.controllers.businessProfileRoutes
import com.amos_tech_code.presentation.controllers.imageRoutes
import com.amos_tech_code.presentation.controllers.notificationRoutes
import com.amos_tech_code.presentation.controllers.resetPasswordRoutes
import com.amos_tech_code.presentation.controllers.statusRoutes
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
        resetPasswordRoutes()
        // Authenticate
        authenticate {
            imageRoutes()
            businessProfileRoutes()
            statusRoutes()
            notificationRoutes()
        }
    }
}
