package com.smart_finance_app.server

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
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
    val email: String,
    val currentPassword: String? = null
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

            if (fullName.length > 100) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Full name must be 100 characters or less"))
            }

            if (email.length > 254) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email address must be 254 characters or less"))
            }

            if (!isValidEmail(email)) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Please enter a valid email address"))
            }

            val currentProfile = getProfileWithPasswordHash(userId)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val emailChanged = email != currentProfile.email

            if (emailChanged) {
                val rateLimitIdentifier = "change-email:$userId"
                val rateLimitAction = "change-email"

                if (isRateLimited(rateLimitIdentifier, rateLimitAction)) {
                    return@put call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("Too many failed attempts. Please try again later.")
                    )
                }

                if (request.currentPassword.isNullOrBlank()) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Current password is required to change email")
                    )
                }


                val passwordCorrect = BCrypt.verifyer()
                    .verify(request.currentPassword.toCharArray(), currentProfile.passwordHash)
                    .verified

                if (!passwordCorrect) {
                    recordFailedAttempt(rateLimitIdentifier, rateLimitAction)

                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Current password is incorrect")
                    )
                }
            }

            val updatedProfile = try {
                updateProfile(userId, fullName, email)
            } catch (exception: SQLException) {
                if (exception.sqlState == "23505") {
                    return@put call.respond(HttpStatusCode.Conflict, ErrorResponse("Email is already in use"))
                }

                throw exception
            }

            if (emailChanged) {
                clearFailedAttempts(
                    identifier = "change-email:$userId",
                    action = "change-email"
                )
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
            clearFailedAttempts(rateLimitIdentifier, rateLimitAction)

            call.respond(ChangePasswordResponse("Password updated successfully"))
        }

        /**
         * DELETE /api/profile/me
         *
         * Permanently deletes the signed-in user and every row that belongs to them.
         * 1. Best-effort: ask TrueLayer to delete its data for each bank connection
         *    (never blocks the account deletion if TrueLayer is unreachable).
         * 2. One DB transaction removes all user-owned rows, then the user row.
         */
        delete("/api/profile/me") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            runCatching { deleteTrueLayerDataForUser(userId) }
                .onFailure { call.application.environment.log.warn("TrueLayer cleanup failed for $userId", it) }

            val deleted = runCatching { deleteUserAndAllData(userId) }
                .getOrElse { exception ->
                    call.application.environment.log.error("Account deletion failed for $userId", exception)
                    return@delete call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Could not delete account")
                    )
                }

            if (!deleted) {
                return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * Tables deleted first, in this order, because other user-owned rows point at them
 * (e.g. transactions.connected_account_id -> connected_accounts.id).
 * Every other table with a user_id column is found automatically below, so new
 * tables (budgets, consents, preferences, subscriptions, support messages, …)
 * are covered without editing this list.
 */
private val orderedUserTables = listOf(
    "transactions",
    "transaction_sync_status",
    "dashboard_layouts",
    "bank_connection_sessions",
    "connected_accounts",
    "refresh_tokens"
)

private fun deleteUserAndAllData(userId: UUID): Boolean =
    Database.dataSource.connection.use { connection ->
        try {
            // Lock the user row so nothing else writes for this user mid-delete.
            val exists = connection.prepareStatement(
                "SELECT 1 FROM users WHERE id = ? FOR UPDATE"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { it.next() }
            }

            if (!exists) {
                connection.rollback()
                return@use false
            }

            // Every real table in the public schema that has a user_id column.
            val discoveredTables = connection.prepareStatement(
                """
                SELECT c.table_name
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = 'public'
                  AND c.column_name = 'user_id'
                  AND t.table_type = 'BASE TABLE'
                  AND c.table_name <> 'users'
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }.toSet()

            val tablesInOrder = orderedUserTables.filter { it in discoveredTables } +
                    (discoveredTables - orderedUserTables.toSet()).sorted()

            tablesInOrder.forEach { table ->
                // Table names come from the database catalogue, never from the request.
                val quoted = "\"" + table.replace("\"", "\"\"") + "\""
                connection.prepareStatement("DELETE FROM $quoted WHERE user_id = ?").use { statement ->
                    statement.setObject(1, userId)
                    statement.executeUpdate()
                }
            }

            connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                statement.setObject(1, userId)
                statement.executeUpdate()
            }

            connection.commit()
            true
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
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

private data class ProfileWithPasswordHash(
    val fullName: String,
    val email: String,
    val passwordHash: String
)

private fun getProfileWithPasswordHash(userId: UUID): ProfileWithPasswordHash? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email, password_hash
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    ProfileWithPasswordHash(
                        fullName = result.getString("full_name"),
                        email = result.getString("email"),
                        passwordHash = result.getString("password_hash")
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