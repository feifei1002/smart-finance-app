package com.smart_finance_app.server.manual

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.CancellationException
import java.sql.SQLException

fun Route.manualAccountRoutes() {
    authenticate("auth-jwt") {
        route("/api/accounts") {

            install(RequestBodyLimit) {
                bodyLimit { 8_192L }
            }
            rateLimit(RateLimitName("accounts-read")) {
                get {
                    val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized, ErrorResponse("Invalid token")
                        )
                    val limit: Int
                    val cursor: ManualAccountCursor?
                    try {
                        limit = parseManualAccountLimit(call.request.queryParameters["limit"])
                        cursor = parseManualAccountCursor(call.request.queryParameters["cursor"])
                    } catch (exception: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(exception.message ?: "Invalid page")
                        )
                    }
                    val page = try {
                        getManualAccountsForUser(userId, limit, cursor)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        call.application.environment.log.error("Could not load manual accounts", exception)
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Could not load accounts")
                        )
                    }
                    call.respond(HttpStatusCode.OK, page)
                }
            }

            rateLimit(RateLimitName("accounts-write")) {
                post {
                    val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized, ErrorResponse("Invalid token")
                        )
                    val requestId = try {
                        parseManualAccountRequestId(call.request.headers["Idempotency-Key"])
                    } catch (exception: IllegalArgumentException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid request ID")
                        )
                    }
                    val request = try {
                        call.receive<CreateManualAccountRequest>()
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: PayloadTooLargeException) {
                        return@post call.respond(
                            HttpStatusCode.PayloadTooLarge, ErrorResponse("Request body is too large")
                        )
                    } catch (_: BadRequestException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest, ErrorResponse("Invalid request body")
                        )
                    }
                    val validated = try {
                        validateManualAccountRequest(request)
                    } catch (exception: ManualAccountValidationException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid account details")
                        )
                    }
                    val result = try {
                        createManualAccount(userId, validated, requestId)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: ManualAccountCreateConflict) {
                        return@post call.respond(
                            HttpStatusCode.Conflict, ErrorResponse("ACCOUNT_CREATE_REQUEST_CONFLICT")
                        )
                    } catch (exception: SQLException) {
                        if (exception.sqlState == "23503") {
                            return@post call.respond(
                                HttpStatusCode.Unauthorized, ErrorResponse("User no longer exists")
                            )
                        }
                        call.application.environment.log.error(
                            "Could not create manual account; SQLState={}", exception.sqlState
                        )
                        return@post call.respond(
                            HttpStatusCode.InternalServerError, ErrorResponse("Could not create account")
                        )
                    } catch (exception: Exception) {
                        call.application.environment.log.error("Could not create manual account", exception)
                        return@post call.respond(
                            HttpStatusCode.InternalServerError, ErrorResponse("Could not create account")
                        )
                    }
                    call.respond(
                        if (result.created) HttpStatusCode.Created else HttpStatusCode.OK,
                        result.account
                    )
                }
            }
        }
    }
}