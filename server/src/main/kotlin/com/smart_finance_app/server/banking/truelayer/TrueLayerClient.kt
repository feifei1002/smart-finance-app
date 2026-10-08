package com.smart_finance_app.server.banking.truelayer

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.smart_finance_app.server.Database
import com.smart_finance_app.server.Encryption
import com.smart_finance_app.server.banking.config.bankingGson
import com.smart_finance_app.server.banking.config.bankingHttpClient
import com.smart_finance_app.server.banking.models.BalanceResponse
import com.smart_finance_app.server.banking.models.StoredAccount
import com.smart_finance_app.server.banking.models.TransactionResponse
import okhttp3.FormBody
import okhttp3.Request
import java.net.URLEncoder
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

internal enum class TrueLayerEnvironment {
    Mock, Sandbox, Production
}

// ── TrueLayer config ─────────────────────────────────────────────────────────
object TrueLayerConfig {
    private val environment: TrueLayerEnvironment
        get() = when (System.getenv("TRUELAYER_ENVIRONMENT")?.lowercase()) {
            "production", "prod" -> TrueLayerEnvironment.Production
            "sandbox" -> TrueLayerEnvironment.Sandbox
            "mock" -> TrueLayerEnvironment.Mock
            else -> TrueLayerEnvironment.Mock
        }

    val clientId: String
        get() = when (environment) {
            TrueLayerEnvironment.Mock ->
                System.getenv("TRUELAYER_MOCK_CLIENT_ID")
            TrueLayerEnvironment.Sandbox ->
                System.getenv("TRUELAYER_SANDBOX_CLIENT_ID")
            TrueLayerEnvironment.Production ->
                System.getenv("TRUELAYER_PROD_CLIENT_ID")
        } ?: error("Missing TrueLayer client ID for $environment")

    val clientSecret: String
        get() = when (environment) {
            TrueLayerEnvironment.Mock ->
                System.getenv("TRUELAYER_MOCK_CLIENT_SECRET")
            TrueLayerEnvironment.Sandbox ->
                System.getenv("TRUELAYER_SANDBOX_CLIENT_SECRET")
            TrueLayerEnvironment.Production ->
                System.getenv("TRUELAYER_PROD_CLIENT_SECRET")
        } ?: error("Missing TrueLayer client secret for $environment")

    val redirectUri: String
        get() = when (environment) {
            TrueLayerEnvironment.Mock ->
                System.getenv("TRUELAYER_MOCK_REDIRECT_URI")
            TrueLayerEnvironment.Sandbox ->
                System.getenv("TRUELAYER_SANDBOX_REDIRECT_URI")
            TrueLayerEnvironment.Production ->
                System.getenv("TRUELAYER_PROD_REDIRECT_URI")
        } ?: error("Missing TrueLayer redirect URI for $environment")

    val isMock: Boolean
        get() = environment == TrueLayerEnvironment.Mock

    val isProduction: Boolean
        get() = environment == TrueLayerEnvironment.Production

    val AUTH_BASE_URL: String
        get() = if (isProduction) {
            "https://auth.truelayer.com"
        } else {
            "https://auth.truelayer-sandbox.com"
        }

    val API_BASE_URL: String
        get() = when (environment) {
            TrueLayerEnvironment.Mock,
            TrueLayerEnvironment.Sandbox -> "https://api.truelayer-sandbox.com"

            TrueLayerEnvironment.Production -> "https://api.truelayer.com"
        }
    val PROVIDERS_BASE_URL: String
        get() = "https://auth.truelayer.com"

    const val SCOPES = "info accounts balance transactions offline_access"
}

// ── Internal data models for TrueLayer JSON responses ────────────────────────

internal data class TrueLayerTokenResponse(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in")    val expiresIn: Int   // seconds
)

internal data class TrueLayerAccountNumber(
    @SerializedName("number")    val number: String?,
    @SerializedName("sort_code") val sortCode: String?
)

internal data class TrueLayerAccount(
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

internal data class TrueLayerProvider(
    @SerializedName("provider_id") val providerId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("logo_url") val logoUrl: String?
)

private data class TrueLayerProvidersResponse(
    val results: List<TrueLayerProvider>
)

// ── TrueLayer API calls ───────────────────────────────────────────────────────

/**
 * Exchanges the one-time auth code for access + refresh tokens.
 */
internal fun exchangeCodeForTokens(code: String): TrueLayerTokenResponse {
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

    val responseBody = bankingHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer token endpoint")
        if (!response.isSuccessful) {
            error("TrueLayer token exchange failed (${response.code}): $body")
        }
        body
    }

    return bankingGson.fromJson(responseBody, TrueLayerTokenResponse::class.java)
}

/**
 * Fetches the list of accounts available under the given access token.
 */
internal fun fetchAccounts(accessToken: String): List<TrueLayerAccount> {
    val request = Request.Builder()
        .url("${TrueLayerConfig.API_BASE_URL}/data/v1/accounts")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

    val responseBody = bankingHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer accounts endpoint")
        if (!response.isSuccessful) {
            error("TrueLayer accounts fetch failed (${response.code}): $body")
        }
        body
    }

    return bankingGson.fromJson(responseBody, TrueLayerAccountsResponse::class.java).results
}

// ── TrueLayer balance + transaction fetchers ─────────────────────────────────

internal fun fetchBalances(accessToken: String, accountId: String): List<BalanceResponse> {
    val request = Request.Builder()
        .url("${TrueLayerConfig.API_BASE_URL}/data/v1/accounts/$accountId/balance")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

    val responseBody = bankingHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer balance endpoint")
        if (!response.isSuccessful) error("TrueLayer balance fetch failed (${response.code}): $body")
        body
    }

    return bankingGson.fromJson(responseBody, TrueLayerBalanceResponse::class.java).results.map {
        BalanceResponse(
            accountId = accountId,
            current = it.current,
            available = it.available,
            currency = it.currency
        )
    }
}

internal fun fetchTransactions(accessToken: String, accountId: String): List<TransactionResponse> {
    // Fetch last 3 months of transactions
    val from = LocalDate.now().minusMonths(3).toString()
    val to   = LocalDate.now().toString()

    val request = Request.Builder()
        .url("${TrueLayerConfig.API_BASE_URL}/data/v1/accounts/$accountId/transactions?from=$from&to=$to")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

    val responseBody = bankingHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()
            ?: error("Empty response from TrueLayer transactions endpoint")
        if (!response.isSuccessful) error("TrueLayer transactions fetch failed (${response.code}): $body")
        body
    }

    return bankingGson.fromJson(responseBody, TrueLayerTransactionsResponse::class.java).results.map {
        TransactionResponse(
            transactionId = it.transactionId,
            timestamp = it.timestamp,
            description = it.description,
            amount = it.amount,
            currency = it.currency,
            type = it.transactionType,
            merchantName = it.merchantName
        )
    }
}

/**
 * Fetches available TrueLayer bank providers for the bank selection screen.
 *
 * Currently TrueLayer returns no providers in sandbox, the app can fall back to
 * the mock bank provider for local testing.
 */
internal fun fetchTrueLayerProviders(): List<TrueLayerProvider> {

    /** Use the production providers endpoint only for displaying the bank list,
     * only the Mock Bank works for sandbox TrueLayer auth connection testing */
    val url = buildString {
        append("${TrueLayerConfig.PROVIDERS_BASE_URL}/api/providers")
        append("?clientId=${URLEncoder.encode(TrueLayerConfig.clientId, "UTF-8")}")
        append("&scopes=${URLEncoder.encode(TrueLayerConfig.SCOPES, "UTF-8")}")
    }

    val request = Request.Builder().url(url).get().build()

    val responseBody = bankingHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string()?: error("Empty response from TrueLayer providers endpoint")

        if(!response.isSuccessful) {
            error("TrueLayer providers fetch failed (${response.code}): $body")
        }

        body
    }

    val json = JsonParser.parseString(responseBody)

    return when {
        json.isJsonObject && json.asJsonObject.has("results") -> {
            bankingGson.fromJson(responseBody, TrueLayerProvidersResponse::class.java).results
        }

        json.isJsonArray -> {
            json.asJsonArray.map {
                bankingGson.fromJson(it, TrueLayerProvider::class.java)
            }
        }

        else -> {
            error("Unexpected provider response: $responseBody")
        }
    }
}

internal fun buildTrueLayerAuthUrl(state: String, providerId: String): String = buildString {
    append(TrueLayerConfig.AUTH_BASE_URL)
    append("/?response_type=code")
    append("&client_id=${URLEncoder.encode(TrueLayerConfig.clientId, "UTF-8")}")
    append("&scope=${URLEncoder.encode(TrueLayerConfig.SCOPES, "UTF-8")}")
    append("&redirect_uri=${URLEncoder.encode(TrueLayerConfig.redirectUri, "UTF-8")}")
    append("&state=${URLEncoder.encode(state, "UTF-8")}")
    append("&providers=${URLEncoder.encode(providerId, "UTF-8")}")
}

/**
 * If the stored token expires within the next 5 minutes, refresh it first.
 * Returns a valid access token to use for the API call.
 */
internal fun ensureFreshToken(stored: StoredAccount): String {
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

    val responseBody = bankingHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: error("Empty response from TrueLayer refresh endpoint")
        if (!response.isSuccessful) error("TrueLayer token refresh failed (${response.code}): $body")
        body
    }

    val newTokens = bankingGson.fromJson(responseBody, TrueLayerTokenResponse::class.java)
    val newExpiry = Instant.now().plusSeconds(newTokens.expiresIn.toLong())

    // Update tokens in database
    Database.dataSource.connection.use { connection ->
        try {
            val updatedRows = connection.prepareStatement(
                """
                UPDATE connected_accounts
                SET access_token = ?, refresh_token = ?, token_expiry = ?, updated_at = NOW()
                WHERE id = ?
                    AND connection_status = 'connected'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, Encryption.encrypt(newTokens.accessToken))
                statement.setString(2, Encryption.encrypt(newTokens.refreshToken))
                statement.setTimestamp(3, Timestamp.from(newExpiry))
                statement.setObject(4, stored.dbId)
                statement.executeUpdate()
            }

            if (updatedRows != 1) {
                error("Account was disconnected before token refresh completed")
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    return newTokens.accessToken
}
