package com.amos_tech_code.plugins

import com.amos_tech_code.configs.FacebookAuthConfig
import com.amos_tech_code.configs.GoogleAuthConfig
import com.amos_tech_code.configs.JwtConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth

fun Application.configureAuthentication() {

    install(Authentication) {
        jwt {
            realm = JwtConfig.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
        oauth("google-oauth") {
            client = HttpClient(CIO) // Apache or CIO
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = GoogleAuthConfig.authorizeUrl,
                    accessTokenUrl = GoogleAuthConfig.tokenUrl,
                    clientId = GoogleAuthConfig.clientId,
                    clientSecret = GoogleAuthConfig.clientSecret,
                    defaultScopes = listOf("profile", "email")
                )
            }
            urlProvider = { GoogleAuthConfig.redirectUri }
        }

        oauth("facebook-oauth") {
            client = HttpClient(CIO)
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "facebook",
                    authorizeUrl = FacebookAuthConfig.authorizeUrl,
                    accessTokenUrl = FacebookAuthConfig.tokenUrl,
                    clientId = FacebookAuthConfig.clientId,
                    clientSecret = FacebookAuthConfig.clientSecret,
                    defaultScopes = listOf("public_profile", "email")
                )
            }
            urlProvider = { FacebookAuthConfig.redirectUri }
        }
    }
}