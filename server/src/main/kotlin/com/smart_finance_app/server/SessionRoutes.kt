package com.smart_finance_app.server

import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.util.UUID

private val defaultAllowedAuthOrigins = setOf(
    "http://localhost:8081",
    "http://127.0.0.1:8081",
    "http://192.168.1.246:8081"
)
private const val REFRESH_COOKIE_NAME = "smart_finance_refresh_token"

const val REFRESH_TOKEN_TRANSPORT_HEADER = "X-Refresh-Token-Transport"

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class RefreshTokenResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val email: String,
    val consentAccepted: Boolean
)

fun Route.sessionRoutes(createAccessToken: (UUID) -> String) {

    post("/auth/refresh") {
        // Reject refresh requests from unexpected web origins before reading or rotating any refresh token.
        if (call.rejectInvalidAuthOrigin()) return@post

        val refreshTokenFromBody = runCatching {
            call.receive<RefreshTokenRequest>().refreshToken
        }.getOrNull()

        val refreshToken = refreshTokenFromBody
            ?: call.request.cookies[REFRESH_COOKIE_NAME]

        if (refreshToken.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing refresh token"))
            return@post
        }

        val rotated = rotateRefreshToken(refreshToken)

        if (rotated == null) {
            call.clearRefreshTokenCookie()
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token is invalid or expired"))
            return@post
        }

        val userId = rotated.userId
        val newRefreshToken = rotated.refreshToken
        val user = getSessionUser(userId)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("User not found")
            )

        call.setRefreshTokenCookie(newRefreshToken)

        call.respond(
            RefreshTokenResponse(
                token = createAccessToken(userId),
                refreshToken = call.refreshTokenForResponse(newRefreshToken),
                userId = userId.toString(),
                name = user.fullName,
                email = user.email,
                consentAccepted = user.consentAccepted
            )
        )
    }

    post("/auth/logout") {

        // Reject logout requests from unexpected web origins before reading or rotating any refresh token.
        if (call.rejectInvalidAuthOrigin()) return@post

        val refreshTokenFromBody = runCatching {
            call.receive<LogoutRequest>().refreshToken
        }.getOrNull()

        val refreshToken = refreshTokenFromBody
            ?: call.request.cookies[REFRESH_COOKIE_NAME]

        if (!refreshToken.isNullOrBlank()) {
            revokeRefreshToken(refreshToken)
        }

        call.clearRefreshTokenCookie()
        call.respond(HttpStatusCode.NoContent)
    }
}

private data class SessionUser(
    val fullName: String,
    val email: String,
    val consentAccepted: Boolean
)

private fun getSessionUser(userId: UUID): SessionUser? {
    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email, consent_accepted_at IS NOT NULL AS consent_accepted
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use {
            it.setObject(1, userId)

            it.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    SessionUser(
                        fullName = result.getString("full_name"),
                        email = result.getString("email"),
                        consentAccepted = result.getBoolean("consent_accepted")
                    )
                }
            }
        }
    }
}
private fun allowedAuthOrigins(): Set<String> {
    return System.getenv("AUTH_ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: defaultAllowedAuthOrigins
}

/*
For local development:
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax

For deployed Web with HTTPS:
COOKIE_SECURE=true
COOKIE_SAME_SITE=None
 */
fun ApplicationCall.setRefreshTokenCookie(refreshToken: String) {
    response.cookies.append(
        Cookie(
            name = REFRESH_COOKIE_NAME,
            value = refreshToken,
            path = "/auth",
            httpOnly = true,
            secure = System.getenv("COOKIE_SECURE")?.toBooleanStrictOrNull() ?: false,
            extensions = mapOf(
                "SameSite" to (System.getenv("COOKIE_SAME_SITE") ?: "Lax")
            ),
            maxAge = 30 * 24 * 60 * 60
        )
    )
}

fun ApplicationCall.clearRefreshTokenCookie() {
    response.cookies.append(
        Cookie(
            name = REFRESH_COOKIE_NAME,
            value = "",
            path = "/auth",
            httpOnly = true,
            secure = System.getenv("COOKIE_SECURE")?.toBooleanStrictOrNull() ?: false,
            extensions = mapOf(
                "SameSite" to (System.getenv("COOKIE_SAME_SITE") ?: "Lax")
            ),
            maxAge = 0
        )
    )
}

/* Web uses an HttpOnly cookie for the refresh token, so JavaScript should not
 receive the raw token. Android still receives it so it can store it securely.
 */
fun ApplicationCall.refreshTokenForResponse(refreshToken: String): String {
    return if (request.headers[REFRESH_TOKEN_TRANSPORT_HEADER] == "cookie") {
        ""
    } else {
        refreshToken
    }
}

/* Protects cookie-based auth routes from cross-site requests. This matters for
 web because the browser automatically sends HttpOnly cookies with requests.
 */
private suspend fun ApplicationCall.rejectInvalidAuthOrigin(): Boolean {
    val origin = request.headers[HttpHeaders.Origin] ?: return false

    if (origin in allowedAuthOrigins()) {
        return false
    }

    respond(HttpStatusCode.Forbidden, ErrorResponse("Invalid request origin"))
    return true
}