package com.smart_finance_app.server.banking.routes

import com.smart_finance_app.server.banking.truelayer.exchangeCodeForTokens
import com.smart_finance_app.server.banking.accounts.failBankConnectionSession
import com.smart_finance_app.server.banking.truelayer.fetchAccounts
import com.smart_finance_app.server.banking.accounts.getPendingBankConnectionSession
import com.smart_finance_app.server.banking.accounts.savePendingBankAccountSelection
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.Instant

internal fun Route.bankCallbackRoutes() {
    /**
     * GET /api/banking/callback
     *
     * TrueLayer redirects here after the user approves access at their bank.
     * This endpoint is NOT protected by JWT — TrueLayer calls it directly.
     * Instead we identify the user via the 'state' parameter we set in /connect.
     *
     * Flow:
     * 1. Extract auth code and userId (state) from query parameters
     * 2. Exchange auth code for access + refresh tokens with TrueLayer
     * 3. Use access token to fetch the user's account list from TrueLayer
     * 4. Save each account + tokens to connected_accounts table
     * 5. Return a success message (frontend will close the browser and refresh)
     */
    get("/api/banking/callback") {
        val code    = call.request.queryParameters["code"]
        val state   = call.request.queryParameters["state"]
        val error   = call.request.queryParameters["error"]

        if (state == null) {
            call.respondText(
                "Missing state parameter",
                status = HttpStatusCode.BadRequest
            )
            return@get
        }

        // TrueLayer sends an error param if the user denied access
        if (error != null) {
            failBankConnectionSession(state)
            call.respondText(
                "Bank connection cancelled: $error",
                status = HttpStatusCode.BadRequest
            )
            return@get
        }

        if (code == null) {
            failBankConnectionSession(state)
            call.respondText(
                "Missing code or state parameter",
                status = HttpStatusCode.BadRequest
            )
            return@get
        }

        val session = getPendingBankConnectionSession(state)
        if (session == null) {
            call.respondText(
                "Invalid or expired connection session",
                status = HttpStatusCode.BadRequest
            )
            return@get
        }

        // ── Step 1: Exchange auth code for tokens ─────────────────────────
        val tokenResponse = runCatching {
            exchangeCodeForTokens(code)
        }.getOrElse {
            failBankConnectionSession(state)
            call.application.environment.log.error("Failed to exchange code for tokens", it)
            call.respondText(
                "Bank connection failed. Please try again.",
                status = HttpStatusCode.InternalServerError
            )
            return@get
        }

        val tokenExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong())

        val accounts = runCatching {
            fetchAccounts(tokenResponse.accessToken)
        }.getOrElse {
            failBankConnectionSession(state)
            call.application.environment.log.error("Failed to fetch accounts from TrueLayer", it)
            call.respondText(
                "Bank connection failed. Please try again.",
                status = HttpStatusCode.InternalServerError
            )
            return@get
        }

        if (accounts.isEmpty()) {
            failBankConnectionSession(state)
            call.respondText(
                "No accounts found for this bank connection",
                status = HttpStatusCode.OK
            )
            return@get
        }

        // ── Step 3: Save each account to the database ─────────────────────
        runCatching {
            savePendingBankAccountSelection(
                state = state,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                tokenExpiry = tokenExpiry,
                accounts = accounts
            )

        }.getOrElse {
            failBankConnectionSession(state)

            call.application.environment.log.error("Failed to save connected accounts", it)

            call.respondText(
                "Bank connection failed. Please try again.",
                status = HttpStatusCode.InternalServerError
            )
            return@get
        }

        // Simple success page — the user sees this in their browser
        // after approving access at their bank
        call.respondText(
            """
            <html>
            <body style="font-family:sans-serif;text-align:center;padding:40px">
                <h2>✅ Bank connected successfully!</h2>
                <p>You can now close this window and return to the app.</p>
            </body>
            </html>
            """.trimIndent(),
            contentType = ContentType.Text.Html,
            status = HttpStatusCode.OK
        )
    }
}