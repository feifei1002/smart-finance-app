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

fun Route.manualTransactionRoutes() {
    authenticate("auth-jwt") {
        route("/api/transactions") {

            install(RequestBodyLimit) {
                bodyLimit { 8_192L }
            }

            rateLimit(RateLimitName("transactions-read")) {
                get {
                    val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized, ErrorResponse("Invalid token")
                        )
                    val page: Int
                    val pageSize: Int
                    val type: ManualTransactionType?
                    try {
                        val query = call.request.queryParameters
                        page = parseManualTransactionPage(query["page"])
                        pageSize = parseManualTransactionPageSize(query["pageSize"])
                        type = parseManualTransactionTypeFilter(query["type"])
                    } catch (exception: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(exception.message ?: "Invalid page")
                        )
                    }
                    val result = try {
                        getManualTransactionsForUser(userId, page, pageSize, type)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        call.application.environment.log.error(
                            "Could not load manual transactions", exception
                        )
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Could not load transactions")
                        )
                    }
                    call.respond(HttpStatusCode.OK, result)
                }
            }

            rateLimit(RateLimitName("transactions-write")) {
                post {
                    val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized, ErrorResponse("Invalid token")
                        )
                    val requestId = try {
                        parseManualAccountRequestId(call.request.headers["Idempotency-Key"])
                    } catch (exception: IllegalArgumentException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(exception.message ?: "Invalid request ID")
                        )
                    }
                    val request = try {
                        call.receive<CreateManualTransactionRequest>()
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
                        validateManualTransactionRequest(request)
                    } catch (exception: ManualTransactionValidationException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(exception.message ?: "Invalid transaction details")
                        )
                    }
                    val result = try {
                        createManualTransaction(userId, validated, requestId)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: ManualTransactionAccountNotFound) {
                        return@post call.respond(
                            HttpStatusCode.NotFound, ErrorResponse("ACCOUNT_NOT_FOUND")
                        )
                    } catch (_: ManualTransactionCreateConflict) {
                        return@post call.respond(
                            HttpStatusCode.Conflict, ErrorResponse("TRANSACTION_CREATE_REQUEST_CONFLICT")
                        )
                    } catch (exception: SQLException) {
                        call.application.environment.log.error(
                            "Could not create manual transaction; SQLState={}", exception.sqlState
                        )
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Could not create transaction")
                        )
                    } catch (exception: Exception) {
                        call.application.environment.log.error(
                            "Could not create manual transaction", exception
                        )
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Could not create transaction")
                        )
                    }
                    call.respond(
                        if (result.created) HttpStatusCode.Created else HttpStatusCode.OK,
                        result.transaction
                    )
                }
            }
        }
    }
}