package com.smart_finance_app.server

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Serializable
data class CreateBankConnectionRequest(val providerId: String, val providerName: String)

// ── Response models ───────────────────────────────────────────────────────────

@Serializable
data class ConnectBankResponse(val authUrl: String)

@Serializable
data class AccountResponse(
    val accountId: String,
    val bankName: String,
    val maskedNumber: String,
    val provider: String
)

@Serializable
data class BalanceResponse(
    val accountId: String,
    val current: Double,
    val available: Double,
    val currency: String
)

@Serializable
data class TransactionResponse(
    val transactionId: String,
    val timestamp: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val type: String,       // CREDIT or DEBIT
    val merchantName: String?
)

@Serializable
data class BankProviderResponse(
    val id: String,
    val name: String,
    val logoUrl: String? = null
)
// ── TrueLayer config ─────────────────────────────────────────────────────────

private object TrueLayerConfig {
    val clientId: String
        get() = System.getenv("TRUELAYER_CLIENT_ID")
            ?: error("Missing environment variable: TRUELAYER_CLIENT_ID")

    val clientSecret: String
        get() = System.getenv("TRUELAYER_CLIENT_SECRET")
            ?: error("Missing environment variable: TRUELAYER_CLIENT_SECRET")

    val redirectUri: String
        get() = System.getenv("TRUELAYER_REDIRECT_URI")
            ?: error("Missing environment variable: TRUELAYER_REDIRECT_URI")

    // Sandbox URLs — switch to truelayer.com (without -sandbox) for production
    const val AUTH_BASE_URL = "https://auth.truelayer-sandbox.com"
    const val API_BASE_URL  = "https://api.truelayer-sandbox.com"

    const val PROVIDERS_BASE_URL = "https://auth.truelayer.com"

    const val SCOPES = "info accounts balance transactions offline_access"
}

// ── Internal data models for TrueLayer JSON responses ────────────────────────

private data class TrueLayerTokenResponse(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in")    val expiresIn: Int   // seconds
)

private data class TrueLayerAccountNumber(
    @SerializedName("number")    val number: String?,
    @SerializedName("sort_code") val sortCode: String?
)

private data class TrueLayerAccount(
    @SerializedName("account_id")     val accountId: String,
    @SerializedName("display_name")   val displayName: String,
    @SerializedName("account_number") val accountNumber: TrueLayerAccountNumber?
)

private data class TrueLayerAccountsResponse(
    val results: List<TrueLayerAccount>
)

private data class TrueLayerBalance(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("current")    val current: Double,
    @SerializedName("available")  val available: Double,
    @SerializedName("currency")   val currency: String
)

private data class TrueLayerBalanceResponse(
    val results: List<TrueLayerBalance>
)

private data class TrueLayerTransaction(
    @SerializedName("transaction_id")   val transactionId: String,
    @SerializedName("timestamp")        val timestamp: String,
    @SerializedName("description")      val description: String,
    @SerializedName("amount")           val amount: Double,
    @SerializedName("currency")         val currency: String,
    @SerializedName("transaction_type") val transactionType: String,  // CREDIT or DEBIT
    @SerializedName("merchant_name")    val merchantName: String?
)

private data class TrueLayerTransactionsResponse(
    val results: List<TrueLayerTransaction>
)

private data class TrueLayerProvider(
    @SerializedName("provider_id") val providerId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("logo_url") val logoUrl: String?
)

private data class TrueLayerProvidersResponse(
    val results: List<TrueLayerProvider>
)

// ── Shared HTTP client and JSON parser ────────────────────────────────────────

private val httpClient = OkHttpClient()
private val gson = Gson()

// ── Routes ────────────────────────────────────────────────────────────────────

fun Route.bankingRoutes() {
    authenticate("auth-jwt") {

        /**
         * GET /api/banking/connect
         *
         * Returns a TrueLayer authorisation URL for the frontend to open
         * in the device browser. The user selects their bank and approves
         * read-only access. TrueLayer then redirects to /api/banking/callback.
         */
        post("/api/banking/connect") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.userIdOrNull()?:
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))


            val request = call.receive<CreateBankConnectionRequest>()
            val state = UUID.randomUUID().toString()

            createBankConnectionSession(
                userId = userId,
                state = state,
                providerId = request.providerId,
                providerName = request.providerName
            )

            val authUrl = buildTrueLayerAuthUrl(state = state, providerId = request.providerId)
            call.respond(ConnectBankResponse(authUrl = authUrl))
        }

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

            val storedAccounts = getStoredAccountsWithTokens(UUID.fromString(userId))
            if (storedAccounts.isEmpty()) {
                call.respond(emptyList<TransactionResponse>())
                return@get
            }

            val transactions = mutableListOf<TransactionResponse>()
            storedAccounts.forEach { stored ->
                val token = ensureFreshToken(stored)
                val tlTransactions = fetchTransactions(token, stored.accountId)
                transactions.addAll(tlTransactions)
            }

            // Sort all transactions newest first across all accounts
            call.respond(transactions.sortedByDescending { it.timestamp })
        }

        get("/api/banking/providers") {
            val providers = runCatching {
                fetchTrueLayerProviders()
            }.getOrElse { exception ->
                call.respond(
                    HttpStatusCode.BadGateway,
                    ErrorResponse("Could not load TrueLayer providers: ${exception.message}")
                )
                return@get
            }

            call.respond(
                providers.map {
                    BankProviderResponse(
                        id = it.providerId,
                        name = it.displayName,
                        logoUrl = it.logoUrl
                    )
                }
            )
        }
    }

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
            markBankConnectionSession(state, "failed")
            call.respondText(
                "Bank connection cancelled: $error",
                status = HttpStatusCode.BadRequest
            )
            return@get
        }

        if (code == null) {
            markBankConnectionSession(state, "failed")
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

//        val userId = session.userId

        // ── Step 1: Exchange auth code for tokens ─────────────────────────
        val tokenResponse = runCatching {
            exchangeCodeForTokens(code)
        }.getOrElse {
            markBankConnectionSession(state, "failed")
            call.respondText(
                "Failed to exchange code for tokens: ${it.message}",
                status = HttpStatusCode.InternalServerError
            )
            return@get
        }

        val tokenExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong())

        // ── Step 2: Fetch accounts from TrueLayer using access token ──────
        val accounts = runCatching {
            fetchAccounts(tokenResponse.accessToken)
        }.getOrElse {
            markBankConnectionSession(state, "failed")
            call.respondText(
                "Failed to fetch accounts: ${it.message}",
                status = HttpStatusCode.InternalServerError
            )
            return@get
        }

        if (accounts.isEmpty()) {
            markBankConnectionSession(state, "failed")
            call.respondText(
                "No accounts found for this bank connection",
                status = HttpStatusCode.OK
            )
            return@get
        }

        // ── Step 3: Save each account to the database ─────────────────────
        runCatching {
                saveConnectedAccounts(
                userId       = session.userId,
                accounts     = accounts,
                accessToken  = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                tokenExpiry  = tokenExpiry,
                providerId = session.providerId,
                providerName = session.providerName
            )
        }.getOrElse {
            markBankConnectionSession(state, "failed")

            call.respondText(
                "Failed to save connected accounts: ${it.message}",
                status = HttpStatusCode.InternalServerError
            )
            return@get
        }

        markBankConnectionSession(state, "completed")

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
            contentType = io.ktor.http.ContentType.Text.Html,
            status = HttpStatusCode.OK
        )
    }
}

// ── TrueLayer API calls ───────────────────────────────────────────────────────

/**
 * Exchanges the one-time auth code for access + refresh tokens.
 */
private fun exchangeCodeForTokens(code: String): TrueLayerTokenResponse {
    val requestBody = FormBody.Builder()
        .add("grant_type",    "authorization_code")
        .add("client_id",     TrueLayerConfig.clientId)
        .add("client_secret", TrueLayerConfig.clientSecret)
        .add("redirect_uri",  TrueLayerConfig.redirectUri)
        .add("code",          code)
        .build()

    val request = Request.Builder()
        .url("${TrueLayerConfig.AUTH_BASE_URL}/connect/token")
        .post(requestBody)
        .build()

    val responseBody = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer token endpoint")
        if (!response.isSuccessful) {
            error("TrueLayer token exchange failed (${response.code}): $body")
        }
        body
    }

    return gson.fromJson(responseBody, TrueLayerTokenResponse::class.java)
}

/**
 * Fetches the list of accounts available under the given access token.
 */
private fun fetchAccounts(accessToken: String): List<TrueLayerAccount> {
    val request = Request.Builder()
        .url("${TrueLayerConfig.API_BASE_URL}/data/v1/accounts")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

    val responseBody = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer accounts endpoint")
        if (!response.isSuccessful) {
            error("TrueLayer accounts fetch failed (${response.code}): $body")
        }
        body
    }

    return gson.fromJson(responseBody, TrueLayerAccountsResponse::class.java).results
}

// ── TrueLayer balance + transaction fetchers ─────────────────────────────────

private fun fetchBalances(accessToken: String, accountId: String): List<BalanceResponse> {
    val request = Request.Builder()
        .url("${TrueLayerConfig.API_BASE_URL}/data/v1/accounts/$accountId/balance")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

    val responseBody = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer balance endpoint")
        if (!response.isSuccessful) error("TrueLayer balance fetch failed (${response.code}): $body")
        body
    }

    return gson.fromJson(responseBody, TrueLayerBalanceResponse::class.java).results.map {
        BalanceResponse(
            accountId = accountId,
            current   = it.current,
            available = it.available,
            currency  = it.currency
        )
    }
}

private fun fetchTransactions(accessToken: String, accountId: String): List<TransactionResponse> {
    // Fetch last 3 months of transactions
    val from = java.time.LocalDate.now().minusMonths(3).toString()
    val to   = java.time.LocalDate.now().toString()

    val request = Request.Builder()
        .url("${TrueLayerConfig.API_BASE_URL}/data/v1/accounts/$accountId/transactions?from=$from&to=$to")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

    val responseBody = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer transactions endpoint")
        if (!response.isSuccessful) error("TrueLayer transactions fetch failed (${response.code}): $body")
        body
    }

    return gson.fromJson(responseBody, TrueLayerTransactionsResponse::class.java).results.map {
        TransactionResponse(
            transactionId = it.transactionId,
            timestamp     = it.timestamp,
            description   = it.description,
            amount        = it.amount,
            currency      = it.currency,
            type          = it.transactionType,
            merchantName  = it.merchantName
        )
    }
}

// ── Token refresh ─────────────────────────────────────────────────────────────

private data class StoredAccount(
    val accountId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiry: Instant,
    val dbId: UUID
)

/**
 * If the stored token expires within the next 5 minutes, refresh it first.
 * Returns a valid access token to use for the API call.
 */
private fun ensureFreshToken(stored: StoredAccount): String {
    val expiresIn5Min = Instant.now().plusSeconds(300)
    if (stored.tokenExpiry.isAfter(expiresIn5Min)) {
        return stored.accessToken // token still valid
    }

    // Token expired — use refresh token to get a new one
    val requestBody = FormBody.Builder()
        .add("grant_type",    "refresh_token")
        .add("client_id",     TrueLayerConfig.clientId)
        .add("client_secret", TrueLayerConfig.clientSecret)
        .add("refresh_token", stored.refreshToken)
        .build()

    val request = Request.Builder()
        .url("${TrueLayerConfig.AUTH_BASE_URL}/connect/token")
        .post(requestBody)
        .build()

    val responseBody = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: error("Empty response from TrueLayer refresh endpoint")
        if (!response.isSuccessful) error("TrueLayer token refresh failed (${response.code}): $body")
        body
    }

    val newTokens = gson.fromJson(responseBody, TrueLayerTokenResponse::class.java)
    val newExpiry = Instant.now().plusSeconds(newTokens.expiresIn.toLong())

    // Update tokens in database
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE connected_accounts
                SET access_token = ?, refresh_token = ?, token_expiry = ?, updated_at = NOW()
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, Encryption.encrypt(newTokens.accessToken))
                statement.setString(2, Encryption.encrypt(newTokens.refreshToken))
                statement.setTimestamp(3, Timestamp.from(newExpiry))
                statement.setObject(4, stored.dbId)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    return newTokens.accessToken
}

// ── Database operations ───────────────────────────────────────────────────────

/**
 * Returns connected accounts for the accounts screen (no tokens exposed).
 */
private fun getConnectedAccountsForUser(userId: UUID): List<AccountResponse> =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT account_id, bank_name, provider, account_number
            FROM connected_accounts
            WHERE user_id = ?
            ORDER BY created_at ASC
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                val accounts = mutableListOf<AccountResponse>()
                while (result.next()) {
                    val realAccountNumber = result.getString("account_number")
                    // Mask the real bank account number — show only last 4 digits
                    val masked = if (!realAccountNumber.isNullOrBlank())
                        realAccountNumber.takeLast(4)
                    else
                        "****"  // fallback if bank didn't return account number
                    accounts.add(
                        AccountResponse(
                            accountId    = result.getString("account_id"),
                            bankName     = result.getString("bank_name"),
                            maskedNumber = masked,
                            provider     = result.getString("provider")
                        )
                    )
                }
                accounts
            }
        }
    }

/**
 * Returns connected accounts with tokens — used internally for API calls.
 */
private fun getStoredAccountsWithTokens(userId: UUID): List<StoredAccount> =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, account_id, access_token, refresh_token, token_expiry
            FROM connected_accounts
            WHERE user_id = ?
            ORDER BY created_at ASC
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                val accounts = mutableListOf<StoredAccount>()
                while (result.next()) {
                    accounts.add(
                        StoredAccount(
                            dbId         = result.getObject("id", UUID::class.java),
                            accountId    = result.getString("account_id"),
                            accessToken  = Encryption.decrypt(result.getString("access_token")),
                            refreshToken = Encryption.decrypt(result.getString("refresh_token")),
                            tokenExpiry  = result.getTimestamp("token_expiry").toInstant()
                        )
                    )
                }
                accounts
            }
        }
    }

/**
 * Saves each connected account to the connected_accounts table.
 * Uses INSERT ... ON CONFLICT DO UPDATE so reconnecting the same
 * bank just refreshes the tokens rather than creating duplicates.
 */
private fun saveConnectedAccounts(
    userId: UUID,
    accounts: List<TrueLayerAccount>,
    accessToken: String,
    refreshToken: String,
    tokenExpiry: Instant,
    providerId: String,
    providerName: String
) {
    Database.dataSource.connection.use { connection ->
        try {
            accounts.forEach { account ->
                connection.prepareStatement(
                    """
                    INSERT INTO connected_accounts
                        (user_id, bank_name, account_id, access_token, refresh_token, token_expiry, provider, account_number, connection_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'connected')
                    ON CONFLICT (user_id, account_id)
                    DO UPDATE SET
                        access_token   = EXCLUDED.access_token,
                        refresh_token  = EXCLUDED.refresh_token,
                        token_expiry   = EXCLUDED.token_expiry,
                        account_number = EXCLUDED.account_number,
                        connection_status = 'connected',
                        updated_at     = NOW()
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.setString(2,  providerName)
                    statement.setString(3, account.accountId)
                    statement.setString(4, Encryption.encrypt(accessToken))   // encrypted at rest
                    statement.setString(5, Encryption.encrypt(refreshToken))  // encrypted at rest
                    statement.setTimestamp(6, Timestamp.from(tokenExpiry))
                    statement.setString(7, providerId)
                    statement.setString(8, account.accountNumber?.number)      // real account number
                    statement.executeUpdate()
                }
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

/**
 * Creates a pending bank connection session before sending the user to TrueLayer.
 *
 * The random state value is stored so the callback can be validated later.
 * This prevents trusting user IDs directly from the callback URL.
 */
private fun createBankConnectionSession(userId: UUID, state: String, providerId: String, providerName: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO bank_connection_sessions
                    (user_id, state, provider_id, provider_name, status)
                    VALUES (?, ?, ?, ?, 'pending')
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, state)
                statement.setObject(3, providerId)
                statement.setObject(4, providerName)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

/**
 * Builds the TrueLayer authorisation URL for the selected bank provider.
 *
 * The frontend opens this URL in the browser so the user can authenticate
 * with their bank and grant read-only access.
 */
private fun buildTrueLayerAuthUrl(state: String, providerId: String): String = buildString {
    append(TrueLayerConfig.AUTH_BASE_URL)
    append("/?response_type=code")
    append("&client_id=${URLEncoder.encode(TrueLayerConfig.clientId, "UTF-8")}")
    append("&scope=${URLEncoder.encode(TrueLayerConfig.SCOPES, "UTF-8")}")
    append("&redirect_uri=${URLEncoder.encode(TrueLayerConfig.redirectUri, "UTF-8")}")
    append("&state=${URLEncoder.encode(state, "UTF-8")}")
    append("&providers=${URLEncoder.encode(providerId, "UTF-8")}")
}

/**
 * Fetches available TrueLayer bank providers for the bank selection screen.
 *
 * Currently TrueLayer returns no providers in sandbox, the app can fall back to
 * the mock bank provider for local testing.
 */
private fun fetchTrueLayerProviders(): List<TrueLayerProvider> {

    /** Use the production providers endpoint only for displaying the bank list,
    * only the Mock Bank works for sandbox TrueLayer auth connection testing */
    val url = buildString {
        append("${TrueLayerConfig.PROVIDERS_BASE_URL}/api/providers")
    }

    val request = Request.Builder().url(url).get().build()

    val responseBody = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()?: error("Empty response from TrueLayer providers endpoint")

        if(!response.isSuccessful) {
            error("TrueLayer providers fetch failed (${response.code}): $body")
        }

        body
    }

    val json = JsonParser.parseString(responseBody)

    return when {
        json.isJsonObject && json.asJsonObject.has("results") -> {
            gson.fromJson(responseBody, TrueLayerProvidersResponse::class.java).results
        }

        json.isJsonArray -> {
            json.asJsonArray.map {
                gson.fromJson(it, TrueLayerProvider::class.java)
            }
        }

        else -> {
            error("Unexpected provider response: $responseBody")
        }
    }
}

private data class BankConnectionSession(val userId: UUID, val providerId: String, val providerName: String)

/**
* Finds a pending bank connection session by its state value.
*
* Returns null if the state is unknown, already completed, failed, or expired.
*/
private fun getPendingBankConnectionSession(state: String): BankConnectionSession? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT user_id, provider_id, provider_name
                FROM bank_connection_sessions
                WHERE state = ? AND status = 'pending'
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, state)
            statement.executeQuery().use { result ->
                if(!result.next()) null else BankConnectionSession(
                    userId = result.getObject("user_id", UUID::class.java),
                    providerId = result.getString("provider_id"),
                    providerName = result.getString("provider_name")
                )
            }
        }
    }

/**
 * Updates the status of a bank connection session.
 *
 * Used to mark the flow as completed after accounts are saved, or failed when
 * TrueLayer returns an error or the callback cannot be processed.
 */
private fun markBankConnectionSession(state: String, status: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET status = ?, completed_at = now()
                WHERE state = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, status)
                statement.setString(2, state)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

/**
 * Extracts the authenticated app user ID from the JWT principal.
 *
 * Returns null if the token does not contain a valid UUID userId claim.
 */
private fun JWTPrincipal.userIdOrNull(): UUID? {
    val userIdValue = payload
        .getClaim("userId")
        .asString()

    return runCatching {
        UUID.fromString(userIdValue)
    }.getOrNull()
}