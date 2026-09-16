package com.smart_finance_app.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import kotlinx.serialization.Serializable
import java.util.UUID

private val allowedLanguages = setOf("en", "es", "fr", "nl", "de", "it", "pl", "zh-TW")

@Serializable
data class UpdateLanguageRequest(val language: String)

@Serializable
data class UpdateLanguageResponse(val language: String)

fun Route.userPreferencesRoutes() {
    authenticate("auth-jwt") {

        /**
         * PATCH /api/user/preferences/language
         * Updates the language preference for the authenticated user.
         */
        patch("/api/user/preferences/language") {
            val userId = call.principal<JWTPrincipal>()
                ?.payload?.getClaim("userId")?.asString()
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@patch
                }

            val request = runCatching { call.receive<UpdateLanguageRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                    return@patch
                }

            if (request.language !in allowedLanguages) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid language code. Must be one of: ${allowedLanguages.joinToString()}")
                )
                return@patch
            }

            val updated = Database.dataSource.connection.use { connection ->
                try {
                    val rows = connection.prepareStatement(
                        "UPDATE users SET language = ? WHERE id = ?"
                    ).use { stmt ->
                        stmt.setString(1, request.language)
                        stmt.setObject(2, userId)
                        stmt.executeUpdate()
                    }
                    connection.commit()
                    rows > 0
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }

            if (updated) {
                call.respond(HttpStatusCode.OK, UpdateLanguageResponse(request.language))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }
    }
}