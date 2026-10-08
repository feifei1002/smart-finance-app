package com.smart_finance_app.server.profile

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.banking.truelayer.deleteTrueLayerDataForUser
import com.smart_finance_app.server.clearFailedAttempts
import com.smart_finance_app.server.common.userIdOrNull
import com.smart_finance_app.server.isRateLimited
import com.smart_finance_app.server.recordFailedAttempt
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
import java.sql.SQLException
import java.util.UUID

fun Route.profileRoutes() {
    authenticate("auth-jwt") {
        get("/api/profile/me") {
            val userId = call.authenticatedUserId() ?: return@get

            val profile = getProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            call.respond(profile)
        }

        put("/api/profile/me") {
            val userId = call.authenticatedUserId() ?: return@put
            val request = call.receiveUpdateProfileRequest() ?: return@put

            val fullName = request.fullName.trim()
            val email = request.email.trim().lowercase()

            validateProfileUpdate(fullName, email)?.let { message ->
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
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

                val passwordCorrect = verifyPassword(
                    rawPassword = request.currentPassword,
                    passwordHash = currentProfile.passwordHash
                )

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
                    return@put call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("Email is already in use")
                    )
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
            val userId = call.authenticatedUserId() ?: return@post
            val request = call.receiveChangePasswordRequest() ?: return@post

            val rateLimitIdentifier = "change-password:$userId"
            val rateLimitAction = "change-password"

            if (isRateLimited(rateLimitIdentifier, rateLimitAction)) {
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("Too many failed attempts. Please try again later.")
                )
            }

            validateNewPassword(request.newPassword)?.let { message ->
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
            }

            val currentHash = getPasswordHash(userId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val currentPasswordCorrect = verifyPassword(
                rawPassword = request.currentPassword,
                passwordHash = currentHash
            )

            if (!currentPasswordCorrect) {
                recordFailedAttempt(rateLimitIdentifier, rateLimitAction)

                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("Current password is incorrect")
                )
            }

            val sameAsCurrentPassword = verifyPassword(
                rawPassword = request.newPassword,
                passwordHash = currentHash
            )

            if (sameAsCurrentPassword) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("New password must be different from your current password")
                )
            }

            val newPasswordHash = hashPassword(request.newPassword)

            updatePasswordAndRevokeSessions(userId, newPasswordHash)
            clearFailedAttempts(rateLimitIdentifier, rateLimitAction)

            call.respond(ChangePasswordResponse("Password updated successfully"))
        }

        /**
         * Permanently deletes the signed-in user and all user-owned rows.
         *
         * TrueLayer cleanup is best-effort and never blocks local account deletion.
         */
        delete("/api/profile/me") {
            val userId = call.authenticatedUserId() ?: return@delete

            runCatching {
                deleteTrueLayerDataForUser(userId)
            }.onFailure {
                call.application.environment.log.warn("TrueLayer cleanup failed for $userId", it)
            }

            val deleted = runCatching {
                deleteUserAndAllData(userId)
            }.getOrElse { exception ->
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

private suspend fun ApplicationCall.authenticatedUserId(): UUID? {
    val userId = principal<JWTPrincipal>()?.userIdOrNull()

    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
    }

    return userId
}

private suspend fun ApplicationCall.receiveUpdateProfileRequest(): UpdateProfileRequest? {
    return runCatching {
        receive<UpdateProfileRequest>()
    }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
        null
    }
}

private suspend fun ApplicationCall.receiveChangePasswordRequest(): ChangePasswordRequest? {
    return runCatching {
        receive<ChangePasswordRequest>()
    }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
        null
    }
}