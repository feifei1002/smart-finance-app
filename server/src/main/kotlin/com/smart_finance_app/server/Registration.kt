package com.smart_finance_app.server

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import java.sql.SQLException
import java.util.UUID

@Serializable
data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String
)
@Serializable
data class RegisterResponse (
    val id: String,
    val email: String
)
@Serializable
data class ErrorResponse (
    val message: String
)

fun Route.registrationRoutes() {
    post("/auth/register") {
        val request = runCatching { call.receive<RegisterRequest>() }
            .getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                return@post
            }

        val name = request.fullName.trim()
        val email = request.email.trim().lowercase()
        val passwordBytes = request.password.encodeToByteArray()

        if(name.isBlank() || !email.contains("@")) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid name or email"))
            return@post
        }

        if(request.password.length < 8 || passwordBytes.size > 72) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be more than 8 characters"))
            return@post
        }

        val passwordHash = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())
        val userId = try {
            createUser(name, email, passwordHash)
        } catch (exception: SQLException) {
            if (exception.sqlState == "23505") {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Email is already registered"))
                return@post
            }
            throw exception
        }

        call.respond(
            HttpStatusCode.Created,
            RegisterResponse(userId.toString(), email)
        )
    }
}

private fun createUser (
    fullName: String,
    email: String,
    passwordHash: String
): UUID = Database.dataSource.connection.use { connection ->
    try {
        connection.prepareStatement(
            """
                INSERT INTO users (full_name, email, password_hash)
                VALUES (?, ?, ?)
                RETURNING id
                """.trimIndent()
        ).use { statement ->
            statement.setString(1, fullName)
            statement.setString(2, email)
            statement.setString(3, passwordHash)

            statement.executeQuery().use { result ->
                check(result.next())
                val id = result.getObject("id", UUID::class.java)
                connection.commit()
                id
            }
        }
    } catch (exception: Exception) {
        connection.rollback()
        throw exception
    }
}