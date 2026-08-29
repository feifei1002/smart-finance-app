package com.smart_finance_app.server

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.util.UUID

private const val REFRESH_COOKIE_NAME = "smart_finance_refresh_token"

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
        val refreshTokenFromBody = runCatching {
            call.receive<RefreshTokenRequest>().refreshToken
        }.getOrNull()

        val refreshToken = refreshTokenFromBody
            ?: call.request.cookies[REFRESH_COOKIE_NAME]

        if (refreshToken.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing refresh token"))
            return@post
        }

        val userId = validateRefreshToken(refreshToken)

        if (userId == null) {
            call.clearRefreshTokenCookie()
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Refresh token is invalid or expired"))
            return@post
        }

        revokeRefreshToken(refreshToken)

        val user = getSessionUser(userId)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not found"))

        val newRefreshToken = createRefreshToken(userId)
        call.setRefreshTokenCookie(newRefreshToken)

        call.respond(
            RefreshTokenResponse(
                token = createAccessToken(userId),
                refreshToken = newRefreshToken,
                userId = userId.toString(),
                name = user.fullName,
                email = user.email,
                consentAccepted = user.consentAccepted
            )
        )
    }

    post("/auth/logout") {
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
//For local development:
//COOKIE_SECURE=false
//COOKIE_SAME_SITE=Lax

//For deployed Web with HTTPS:
//COOKIE_SECURE=true
//COOKIE_SAME_SITE=None
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