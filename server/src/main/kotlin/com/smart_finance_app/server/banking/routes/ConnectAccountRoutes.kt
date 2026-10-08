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

        val storedAccounts = getStoredAccountsWithTokens(UUID.fromString(userId))
        if (storedAccounts.isEmpty()) {
            call.respond(emptyList<BalanceResponse>())
            return@get
        }

        val balances = mutableListOf<BalanceResponse>()
        storedAccounts.forEach { stored ->
            val token = ensureFreshToken(stored)
            val tlBalances = fetchBalances(token, stored.accountId)
            balances.addAll(tlBalances)
        }

        call.respond(balances)
    }
}