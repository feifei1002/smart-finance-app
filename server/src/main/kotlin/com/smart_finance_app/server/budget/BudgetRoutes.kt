package com.smart_finance_app.server.budget

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.util.UUID

fun Route.budgetRoutes() {
    authenticate("auth-jwt") {
        get("/api/budgets") {
            val userId = call.authenticatedUserId() ?: return@get

            call.respond(getBudgetsForUser(userId))
        }

        post("/api/budgets") {
            val userId = call.authenticatedUserId() ?: return@post
            val request = call.receiveBudgetRequest() ?: return@post

            validateBudgetRequest(request)?.let { message ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
                return@post
            }

            try {
                val budget = createBudget(userId, request)
                call.respond(HttpStatusCode.Created, budget)
            } catch (exception: Exception) {
                call.respondBudgetSaveError(exception, request)
            }
        }

        put("/api/budgets/{id}") {
            val userId = call.authenticatedUserId() ?: return@put
            val budgetId = call.budgetIdParameter() ?: return@put
            val request = call.receiveBudgetRequest() ?: return@put

            validateBudgetRequest(request)?.let { message ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(message))
                return@put
            }

            try {
                val updated = updateBudget(userId, budgetId, request)

                if (!updated) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Budget not found"))
                    return@put
                }

                call.respond(HttpStatusCode.OK, ErrorResponse("Budget updated"))
            } catch (exception: Exception) {
                call.respondBudgetSaveError(exception, request)
            }
        }

        delete("/api/budgets/{id}") {
            val userId = call.authenticatedUserId() ?: return@delete
            val budgetId = call.budgetIdParameter() ?: return@delete

            val deleted = deleteBudget(userId, budgetId)

            if (!deleted) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Budget not found"))
                return@delete
            }

            call.respond(HttpStatusCode.OK, ErrorResponse("Budget deleted"))
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

private suspend fun ApplicationCall.budgetIdParameter(): UUID? {
    val budgetId = parameters["id"]?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }

    if (budgetId == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid budget ID"))
    }

    return budgetId
}

private suspend fun ApplicationCall.receiveBudgetRequest(): BudgetRequest? {
    return runCatching {
        receive<BudgetRequest>()
    }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        null
    }
}

private suspend fun ApplicationCall.respondBudgetSaveError(
    exception: Exception,
    request: BudgetRequest
) {
    if (isDuplicateBudgetError(exception)) {
        respond(
            HttpStatusCode.Conflict,
            ErrorResponse("A ${request.period} budget for ${request.category} already exists")
        )
    } else {
        respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse("Something went wrong while saving your budget. Please try again.")
        )
    }
}