package com.smart_finance_app.server.manual

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.CancellationException
import java.sql.SQLException

fun Route.manualAccountRoutes() {
    authenticate("auth-jwt") {
        get("/api/accounts") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.Unauthorized, ErrorResponse("Invalid token")
                )

            val accounts = try {
                getManualAccountsForUser(userId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                call.application.environment.log.error("Could not load manual accounts", exception)
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Could not load accounts"
                    )
                )
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/api/accounts") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, ErrorResponse("Invalid token")
                )

            val request = try {
                call.receive<CreateManualAccountRequest>()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: BadRequestException) {
                return@post call.respond(
                    HttpStatusCode.BadRequest, ErrorResponse("Invalid request body")
                )
            }

            val validated = try {
                validateManualAccountRequest(request)
            } catch (exception: ManualAccountValidationException) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(exception.message ?: "Invalid account details")
                )
            }

            val account = try {
                createManualAccount(userId, validated)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SQLException) {
                // A user can be deleted after JWT validation but before the insert.
                if (exception.sqlState == "23503") {
                    return@post call.respond(
                        HttpStatusCode.Unauthorized, ErrorResponse("User no longer exists")
                    )
                }
                call.application.environment.log.error("Could not create manual account", exception)
                return@post call.respond(
                    HttpStatusCode.InternalServerError, ErrorResponse("Could not create account")
                )
            } catch (exception: Exception) {
                call.application.environment.log.error("Could not create manual account", exception)
                return@post call.respond(
                    HttpStatusCode.InternalServerError, ErrorResponse("Could not create account")
                )
            }
            call.respond(HttpStatusCode.Created, account)
        }
    }
}