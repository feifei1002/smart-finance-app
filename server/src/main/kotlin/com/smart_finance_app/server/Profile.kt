package com.smart_finance_app.server

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import java.sql.SQLException
import java.util.UUID

@Serializable
data class ProfileResponse(
    val fullName: String,
    val email: String
)

@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val email: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ChangePasswordResponse(
    val message: String
)

fun Route.profileRoutes() {
    authenticate("auth-jwt") {
        get("/api/profile/me") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val profile = getProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            call.respond(profile)
        }

        put("/api/profile/me") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val request = runCatching { call.receive<UpdateProfileRequest>() }
                .getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                }

            val fullName = request.fullName.trim()
            val email = request.email.trim().lowercase()

            if (fullName.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Full name is required"))
            }

            if (!isValidEmail(email)) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Please enter a valid email address"))
            }

            val updatedProfile = try {
                updateProfile(userId, fullName, email)
            } catch (exception: SQLException) {
                if (exception.sqlState == "23505") {
                    return@put call.respond(HttpStatusCode.Conflict, ErrorResponse("Email is already in use"))
                }

                throw exception
            }

            call.respond(updatedProfile)
        }

        post("/api/profile/password") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val rateLimitIdentifier = "change-password:$userId"
            val rateLimitAction = "change-password"

            if (isRateLimited(rateLimitIdentifier, rateLimitAction)) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("Too many failed attempts. Please try again later.")
                )
                return@post
            }

            val request = runCatching { call.receive<ChangePasswordRequest>() }
                .getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                }

            val newPasswordBytes = request.newPassword.encodeToByteArray()

            if (request.newPassword.length < 8 || newPasswordBytes.size > 72) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Password must be at least 8 characters")
                )
            }

            val currentHash = getPasswordHash(userId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val currentPasswordCorrect = BCrypt.verifyer()
                .verify(request.currentPassword.toCharArray(), currentHash)
                .verified

            if (!currentPasswordCorrect) {
                recordFailedAttempt(rateLimitIdentifier, rateLimitAction)

                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Current password is incorrect"))
            }

            val sameAsCurrentPassword = BCrypt.verifyer()
                .verify(request.newPassword.toCharArray(), currentHash)
                .verified

            if (sameAsCurrentPassword) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("New password must be different from your current password")
                )
            }

            val newPasswordHash = BCrypt.withDefaults()
                .hashToString(12, request.newPassword.toCharArray())

            updatePasswordAndRevokeSessions(userId, newPasswordHash)

            call.respond(ChangePasswordResponse("Password updated successfully"))
        }
    }
}

private fun getProfile(userId: UUID): ProfileResponse? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    ProfileResponse(
                        fullName = result.getString("full_name"),
                        email = result.getString("email")
                    )
                }
            }
        }
    }

private fun updateProfile(userId: UUID, fullName: String, email: String): ProfileResponse =
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users
                    SET full_name = ?, email = ?
                    WHERE id = ?
                    RETURNING full_name, email
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, fullName)
                statement.setString(2, email)
                statement.setObject(3, userId)

                statement.executeQuery().use { result ->
                    check(result.next()) { "User not found" }

                    val profile = ProfileResponse(
                        fullName = result.getString("full_name"),
                        email = result.getString("email")
                    )

                    connection.commit()
                    profile
                }
            }
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

private fun getPasswordHash(userId: UUID): String? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT password_hash FROM users WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (result.next()) result.getString("password_hash") else null
            }
        }
    }

private fun updatePasswordAndRevokeSessions(userId: UUID, passwordHash: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE id = ?"
            ).use {
                it.setString(1, passwordHash)
                it.setObject(2, userId)
                it.executeUpdate()
            }

            connection.prepareStatement(
                """
                    UPDATE refresh_tokens
                    SET revoked_at = now()
                    WHERE user_id = ?
                      AND revoked_at IS NULL
                """.trimIndent()
            ).use {
                it.setObject(1, userId)
                it.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun JWTPrincipal.userIdOrNull(): UUID? {
    val userIdValue = payload.getClaim("userId").asString()

    return runCatching {
        UUID.fromString(userIdValue)
    }.getOrNull()
}

private fun isValidEmail(email: String): Boolean {
    return email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
}