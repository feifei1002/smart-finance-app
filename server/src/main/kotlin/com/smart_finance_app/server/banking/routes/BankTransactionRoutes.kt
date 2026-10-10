package com.smart_finance_app.server.banking.routes

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.banking.models.TransactionResponse
import com.smart_finance_app.server.banking.models.UpdateTransactionCategoryRequest
import com.smart_finance_app.server.banking.models.UpdateTransactionCategoryResponse
import com.smart_finance_app.server.banking.transactions.allowedTransactionCategories
import com.smart_finance_app.server.banking.truelayer.ensureFreshToken
import com.smart_finance_app.server.banking.truelayer.fetchTransactions
import com.smart_finance_app.server.banking.transactions.getImportedTransactionsForUser
import com.smart_finance_app.server.banking.transactions.getLastSuccessfulTransactionSync
import com.smart_finance_app.server.banking.accounts.getStoredAccountsWithTokens
import com.smart_finance_app.server.banking.transactions.getTransactionAmountForUser
import com.smart_finance_app.server.banking.transactions.recategorizeTransactionsForUser
import com.smart_finance_app.server.banking.transactions.syncTransactionsForUser
import com.smart_finance_app.server.banking.transactions.updateTransactionCategoryForUser
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID
import kotlin.text.toIntOrNull

internal fun Route.bankTransactionRoutes() {
    get("/api/banking/transactions/imported") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@get call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid token")
            )

        val page = call.request.queryParameters["page"]
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

        val pageSize = call.request.queryParameters["pageSize"]
            ?.toIntOrNull()
            ?.coerceIn(1, 500)
            ?: 25

        val type = call.request.queryParameters["type"]

        call.respond(getImportedTransactionsForUser(userId, page, pageSize, type))
    }

    /**
     * GET /api/banking/transactions
     *
     * Fetches transactions for all connected accounts from TrueLayer.
     * Returns up to 3 months of transaction history per account.
     */
    get("/api/banking/transactions") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.payload?.getClaim("userId")?.asString()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val storedAccounts = runCatching {
            getStoredAccountsWithTokens(UUID.fromString(userId))
        }.getOrElse { exception ->
            call.application.environment.log.error("Could not load connected account tokens", exception)

            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Could not load connected accounts")
            )
            return@get
        }
        
        if (storedAccounts.isEmpty()) {
            call.respond(emptyList<TransactionResponse>())
            return@get
        }

        val transactions = mutableListOf<TransactionResponse>()
        val failedAccounts = mutableListOf<String>()
        var succeededAccounts = 0

        storedAccounts.forEach { stored ->
            runCatching {
                val token = ensureFreshToken(stored)
                val tlTransactions = fetchTransactions(token, stored.accountId)
                    .map { it.copy(accountId = stored.accountId) }

                succeededAccounts++
                transactions.addAll(tlTransactions)
            }.onFailure { exception ->
                failedAccounts.add("${stored.accountName}: ${exception.message ?: "Unknown error"}")
            }
        }

        if (succeededAccounts == 0 && failedAccounts.isNotEmpty()) {
            call.application.environment.log.warn(
                "Raw TrueLayer transaction fetch failed for all accounts: {}",
                failedAccounts.joinToString("; ")
            )

            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Could not fetch transactions from TrueLayer")
            )
            return@get
        }

        if (failedAccounts.isNotEmpty()) {
            call.application.environment.log.warn(
                "Raw TrueLayer transaction fetch partially failed: {}",
                failedAccounts.joinToString("; ")
            )
        }

        // Sort all transactions newest first across all accounts
        call.respond(transactions.sortedByDescending { it.timestamp })
    }

    post("/api/banking/transactions/sync") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid token")
            )

        val result = runCatching {
            syncTransactionsForUser(userId)
        }.getOrElse { exception ->
            val lastSync = getLastSuccessfulTransactionSync(userId)
            call.application.environment.log.error("Transaction sync failed", exception)

            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(
                    "Transaction sync failed. Last successful sync: ${lastSync ?: "Never"}"
                )
            )
            return@post
        }

        call.respond(result)
    }

    put("/api/banking/transactions/{id}/category") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val transactionId = call.parameters["id"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid transaction id"))

        val request = runCatching { call.receive<UpdateTransactionCategoryRequest>() }
            .getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                return@put
            }

        val category = request.category.trim()

        if (category !in allowedTransactionCategories) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid category"))
            return@put
        }

        val transactionAmount = getTransactionAmountForUser(userId, transactionId)
            ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaction not found"))

        if (category == "Income" && transactionAmount < 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Income cannot be assigned to outgoing transactions")
            )
            return@put
        }

        val updated = updateTransactionCategoryForUser(userId, transactionId, category)

        if (!updated) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaction not found"))
            return@put
        }

        call.respond(UpdateTransactionCategoryResponse(transactionId.toString(), category))
    }

    /* Bulk ML recategorization for existing imported transactions.
         This should only update transactions whose category_source is auto.
         Manually edited categories must be preserved.
         */
    post("/api/banking/transactions/recategorize") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid token")
            )

        val expectedAdminToken = System.getenv("RECATEGORIZE_ADMIN_TOKEN")

        if (expectedAdminToken.isNullOrBlank()) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            return@post
        }

        val providedAdminToken = call.request.headers["X-Admin-Token"]

        if (providedAdminToken != expectedAdminToken) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Forbidden"))
            return@post
        }

        val updatedCount = recategorizeTransactionsForUser(userId)
        call.respond(mapOf("updatedCount" to updatedCount))
    }
}