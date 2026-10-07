package com.smart_finance_app.server.banking.routes

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.banking.models.BankConnectionStatusResponse
import com.smart_finance_app.server.banking.models.ConnectBankResponse
import com.smart_finance_app.server.banking.models.CreateBankConnectionRequest
import com.smart_finance_app.server.banking.models.SelectBankAccountsRequest
import com.smart_finance_app.server.banking.truelayer.buildTrueLayerAuthUrl
import com.smart_finance_app.server.banking.accounts.cancelPendingBankAccountSelection
import com.smart_finance_app.server.banking.accounts.createBankConnectionSession
import com.smart_finance_app.server.banking.accounts.expireOldBankConnectionSelections
import com.smart_finance_app.server.banking.accounts.getBankConnectionSessionStatus
import com.smart_finance_app.server.banking.accounts.getConnectedAccountsForUser
import com.smart_finance_app.server.banking.accounts.getPendingSelectableAccounts
import com.smart_finance_app.server.banking.config.maxAccountsForSubscription
import com.smart_finance_app.server.banking.accounts.saveSelectedBankAccounts
import com.smart_finance_app.server.common.userIdOrNull
import com.smart_finance_app.server.subscriptions.getSubscriptionStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

internal fun Route.bankConnectionRoutes() {
    post("/api/banking/connect") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull() ?:
        return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        // Derive limit from subscription plan
        val subscriptionStatus = getSubscriptionStatus(userId)
        val maxAccounts = maxAccountsForSubscription(subscriptionStatus)

        val existingAccounts = getConnectedAccountsForUser(userId)
        if (existingAccounts.size >= maxAccounts) {
            return@post call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("Account limit reached. Your plan allows up to $maxAccounts connected accounts.")
            )
        }


        val request = call.receive<CreateBankConnectionRequest>()
        val state = UUID.randomUUID().toString()

        createBankConnectionSession(
            userId = userId,
            state = state,
            providerId = request.providerId,
            providerName = request.providerName
        )

        val authUrl = buildTrueLayerAuthUrl(state = state, providerId = request.providerId)
        call.respond(ConnectBankResponse(authUrl = authUrl, state = state))
    }


    get("/api/banking/connection-session/{state}") {
        expireOldBankConnectionSelections()
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@get call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid token")
            )

        val state = call.parameters["state"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Missing state")
            )

        val status = getBankConnectionSessionStatus(userId, state)
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Connection session not found")
            )

        call.respond(BankConnectionStatusResponse(status))
    }


    get("/api/banking/connection-session/{state}/accounts") {
        expireOldBankConnectionSelections()
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val state = call.parameters["state"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing state"))

        val accounts = getPendingSelectableAccounts(userId, state)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Connection session not found"))

        call.respond(accounts)
    }

    post("/api/banking/connection-session/{state}/accounts") {
        expireOldBankConnectionSelections()
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val state = call.parameters["state"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing state"))

        val request = call.receive<SelectBankAccountsRequest>()

        val result = saveSelectedBankAccounts(userId, state, request.accountIds)

        if (result) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Could not save selected accounts")
            )
        }
    }

    post("/api/banking/connection-session/{state}/cancel") {
        expireOldBankConnectionSelections()

        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val state = call.parameters["state"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing state"))

        val cancelled = cancelPendingBankAccountSelection(userId, state)

        if (cancelled) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Connection session not found")
            )
        }
    }
}