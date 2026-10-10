package com.smart_finance_app.server.banking.routes

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.banking.models.BalanceResponse
import com.smart_finance_app.server.banking.accounts.disconnectConnectedAccount
import com.smart_finance_app.server.banking.truelayer.ensureFreshToken
import com.smart_finance_app.server.banking.truelayer.fetchBalances
import com.smart_finance_app.server.banking.accounts.getConnectedAccountsForUser
import com.smart_finance_app.server.banking.accounts.getStoredAccountsWithTokens
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import java.util.UUID

internal fun Route.connectedAccountRoutes() {
    /**
     * GET /api/banking/accounts
     *
     * Returns the list of bank accounts the user has connected.
     * Reads from our own database — no TrueLayer call needed here.
     */
    get("/api/banking/accounts") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.payload?.getClaim("userId")?.asString()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val accounts = getConnectedAccountsForUser(UUID.fromString(userId))
        call.respond(accounts)
    }


    delete("/api/banking/accounts/{accountId}") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val accountId = call.parameters["accountId"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest)

        val disconnected = disconnectConnectedAccount(userId, accountId)

        if (disconnected) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Connected account not found")
            )
        }
    }


    /**
     * GET /api/banking/balance
     *
     * Fetches the current balance for each connected account from TrueLayer.
     * Automatically refreshes expired tokens before calling TrueLayer.
     */
    get("/api/banking/balance") {
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
            call.respond(emptyList<BalanceResponse>())
            return@get
        }

        val balances = mutableListOf<BalanceResponse>()
        val failedAccounts = mutableListOf<String>()
        storedAccounts.forEach { stored ->
            runCatching {
                val token = ensureFreshToken(stored)
                val tlBalances = fetchBalances(token, stored.accountId)
                balances.addAll(tlBalances)
            }.onFailure { exception ->
                failedAccounts.add("${stored.accountName}: ${exception.message ?: "Unknown error"}")
            }
        }

        if (balances.isEmpty() && failedAccounts.isNotEmpty()) {
            call.application.environment.log.warn(
                "TrueLayer balance fetch failed for all accounts: {}",
                failedAccounts.joinToString("; ")
            )

            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Could not fetch balances from TrueLayer")
            )
            return@get
        }

        if (failedAccounts.isNotEmpty()) {
            call.application.environment.log.warn(
                "TrueLayer balance fetch partially failed: {}",
                failedAccounts.joinToString("; ")
            )
        }

        call.respond(balances)
    }
}