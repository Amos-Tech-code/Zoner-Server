package com.amos_tech_code

import com.amos_tech_code.data.database.DatabaseFactory
import com.amos_tech_code.data.database.migrateDatabase
import com.amos_tech_code.data.database.seedDatabase
import com.amos_tech_code.application.plugins.configureAuthentication
import com.amos_tech_code.application.plugins.configureKoin
import com.amos_tech_code.application.plugins.configureMediaServices
import com.amos_tech_code.application.plugins.configureRouting
import com.amos_tech_code.domain.services.FirebaseService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import java.time.Duration

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureMediaServices()
    configureAuthentication() // Call authentication config
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(CallLogging)
    configureRouting()
    FirebaseService // Initialize Firebase
    DatabaseFactory.init() // Initialize the database
    migrateDatabase()     // Run migrations if needed
    seedDatabase()        // Seed the database

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

}
