package com.amos_tech_code

import com.amos_tech_code.configs.FacebookAuthConfig
import com.amos_tech_code.configs.GoogleAuthConfig
import com.amos_tech_code.configs.JwtConfig
import com.amos_tech_code.database.DatabaseFactory
import com.amos_tech_code.database.migrateDatabase
import com.amos_tech_code.database.seedDatabase
import com.amos_tech_code.plugins.configureAuthentication
import com.amos_tech_code.plugins.configureKoin
import com.amos_tech_code.plugins.configureRouting
import com.amos_tech_code.services.FirebaseService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
