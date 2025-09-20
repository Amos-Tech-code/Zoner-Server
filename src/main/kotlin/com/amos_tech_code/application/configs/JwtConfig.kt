package com.amos_tech_code.application.configs

import com.amos_tech_code.domain.model.UserRole
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    private val secret = AppConfig.JWT_SECRET
    private val validityInMs = AppConfig.JWT_EXPIRATION
    val issuer = AppConfig.JWT_ISSUER.toString()
    val audience = AppConfig.JWT_AUDIENCE.toString()
    val realm = AppConfig.JWT_REALM.toString()

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(userId: String, role: UserRole): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("role", role.name)
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
        .sign(algorithm)

}