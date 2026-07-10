package com.smart_finance_app.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

fun Route.consentRoutes() {
    authenticate("auth-jwt") {
        post("/auth/consent") {
            val userIdValue = call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim("userId")
                ?.asString()

            val userId = runCatching {
                UUID.fromString(userIdValue)
            }.getOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val updated = Database.dataSource.connection.use { connection ->
                try {
                    val count = connection.prepareStatement(
                        """
                            UPDATE users SET consent_accepted_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }

                    connection.commit()
                    count
                } catch (exception: Exception) {
                    connection.rollback()
                    throw exception
                } finally {
                    connection.autoCommit = true
                }
            }

            if (updated == 1) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }
    }
}