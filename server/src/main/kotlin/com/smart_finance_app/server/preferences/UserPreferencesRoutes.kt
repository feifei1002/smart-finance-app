package com.smart_finance_app.server.preferences

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import java.util.UUID

fun Route.userPreferencesRoutes() {
    authenticate("auth-jwt") {

        /**
         * PATCH /api/user/preferences/language
         * Updates the language preference for the authenticated user.
         */
        patch("/api/user/preferences/language") {
            val userId = call.authenticatedUserId() ?: return@patch
            val request = call.receiveRequest<UpdateLanguageRequest>() ?: return@patch

            validateLanguage(request.language)?.let { message ->
                return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
            }

            val updated = updateUserLanguage(userId, request.language)

            if (updated) {
                call.respond(HttpStatusCode.OK, UpdateLanguageResponse(request.language))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }

        /**
         * PATCH /api/user/preferences/currency
         * Updates the currency preference for the authenticated user.
         */
        patch("/api/user/preferences/currency") {
            val userId = call.authenticatedUserId() ?: return@patch
            val request = call.receiveRequest<UpdateCurrencyRequest>() ?: return@patch

            validateCurrency(request.currency)?.let { message ->
                return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
            }

            val updated = updateUserCurrency(userId, request.currency)

            if (updated) {
                call.respond(HttpStatusCode.OK, UpdateCurrencyResponse(request.currency))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }

        /**
         * PATCH /api/user/preferences/theme
         * Updates the theme preference for the authenticated user.
         */
        patch("/api/user/preferences/theme") {
            val userId = call.authenticatedUserId() ?: return@patch
            val request = call.receiveRequest<UpdateThemeRequest>() ?: return@patch

            validateTheme(request.theme)?.let { message ->
                return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
            }

            val updated = updateUserTheme(userId, request.theme)

            if (updated) {
                call.respond(HttpStatusCode.OK, UpdateThemeResponse(request.theme))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }

        patch("/api/user/preferences") {
            val userId = call.authenticatedUserId() ?: return@patch
            val request = call.receiveRequest<UpdatePreferencesRequest>() ?: return@patch

            validatePreferences(request)?.let { message ->
                return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
            }

            val updated = updateUserPreferences(userId, request)

            if (updated) {
                call.respond(
                    HttpStatusCode.OK,
                    UpdatePreferencesResponse(
                        language = request.language,
                        currency = request.currency,
                        theme = request.theme
                    )
                )
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
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

private suspend inline fun <reified T : Any> ApplicationCall.receiveRequest(): T? {
    return runCatching {
        receive<T>()
    }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        null
    }
}