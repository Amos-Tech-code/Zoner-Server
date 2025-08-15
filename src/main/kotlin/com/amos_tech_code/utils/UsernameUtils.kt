package com.amos_tech_code.utils

// username_utils.kt
import io.ktor.server.plugins.BadRequestException
import java.util.UUID

private val USERNAME_REGEX = Regex("^[a-z0-9._]{3,15}$")

fun normalizeAndValidateUsername(input: String): String {
    val clean = input.removePrefix("@").trim().lowercase()
    if (!USERNAME_REGEX.matches(clean)) {
        throw BadRequestException("Invalid username format. Use 3–15 chars: a-z, 0-9, dot or underscore.")
    }
    return "@$clean"
}

fun extractClean(input: String): String =
    input.removePrefix("@").trim().lowercase()

fun String.toUUIDOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
