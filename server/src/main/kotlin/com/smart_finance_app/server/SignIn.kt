package com.smart_finance_app.server

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class SignInResponse(val token: String, val userId: String, val email: String, val consentAccepted: Boolean)

fun Route.signInRoutes(createToken: (UUID) -> String) {
    post("/auth/signin") {
        val request = runCatching { call.receive<SignInRequest>() }
            .getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                return@post
            }

        val email = request.email.trim().lowercase()

        val user = Database.dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    SELECT id, email, password_hash, consent_accepted_at IS NOT NULL AS consent_accepted
                    FROM users WHERE email = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, email)

                statement.executeQuery().use { result ->
                    if(!result.next()) null
                    else SignInUser(
                        id = result.getObject("id", UUID::class.java),
                        email = result.getString("email"),
                        passwordHash = result.getString("password_hash"),
                        consentAccepted = result.getBoolean("consent_accepted")
                    )
                }
            }
        }

        val passwordCorrect = user != null &&
                BCrypt.verifyer()
                    .verify(request.password.toCharArray(), user.passwordHash).verified

        if (!passwordCorrect) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid email or password")
            )
            return@post
        }

        call.respond(
            SignInResponse(
                token = createToken(user.id),
                userId = user.id.toString(),
                email = user.email,
                consentAccepted = user.consentAccepted
            )
        )
    }
}

private data class SignInUser(val id: UUID, val email: String, val passwordHash: String, val consentAccepted: Boolean)