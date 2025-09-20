package com.amos_tech_code.application.configs

import io.github.cdimascio.dotenv.dotenv


object GoogleAuthConfig {

    private val env = dotenv {
        ignoreIfMissing = true
        filename = ".env"
    }

    val clientId = env["GOOGLE_CLIENT_ID"]
    val clientSecret = env["GOOGLE_CLIENT_SECRET"]
    const val redirectUri = "http://localhost:8080/auth/google/callback"
    const val authorizeUrl = "https://accounts.google.com/o/oauth2/auth"
    const val tokenUrl = "https://oauth2.googleapis.com/token"
}