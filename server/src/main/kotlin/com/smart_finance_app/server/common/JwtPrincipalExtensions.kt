package com.smart_finance_app.server.common

import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.UUID

/**
 * Extracts the authenticated app user ID from the JWT principal.
 *
 * Returns null if the token does not contain a valid UUID userId claim.
 */
internal fun JWTPrincipal.userIdOrNull(): UUID? {
    val userIdValue = payload
        .getClaim("userId")
        .asString()

    return runCatching {
        UUID.fromString(userIdValue)
    }.getOrNull()
}