package com.amos_tech_code.configs

import io.github.cdimascio.dotenv.dotenv


object FacebookAuthConfig {

    private val env = dotenv {
        ignoreIfMissing = true
        filename = ".env"
    }

    val clientId = env["FACEBOOK_CLIENT_ID"]
    val clientSecret = env["FACEBOOK_CLIENT_SECRET"]
    const val redirectUri = "http://localhost:8080/auth/facebook/callback"
    const val authorizeUrl = "https://www.facebook.com/v14.0/dialog/oauth"
    const val tokenUrl = "https://graph.facebook.com/v14.0/oauth/access_token"
}