package com.smart_finance_app.dashboard

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

// ── Response models matching server Banking.kt ────────────────────────────────

@Serializable
data class BalanceData(
    val accountId: String,
    val current: Double,
    val available: Double,
    val currency: String
)

@Serializable
data class TransactionData(
    val transactionId: String,
    val timestamp: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val type: String,           // "CREDIT" or "DEBIT"
    val merchantName: String? = null,
    val accountId: String? = null   // populated once backend includes it; used for per-account filtering
)

@Serializable
data class AccountData(
    val accountId: String,
    val bankName: String,
    val maskedNumber: String,
    val provider: String
)

// ── Sealed results ────────────────────────────────────────────────────────────

sealed interface DashboardResult<out T> {
    data class Success<T>(val data: T) : DashboardResult<T>
    data class Failure(val message: String) : DashboardResult<Nothing>
}

// ── Dashboard layout sync ─────────────────────────────────────────────────────

@Serializable
data class DashboardLayoutDto(
    val cardOrder: String     = "",
    val deletedCards: String  = "",
    val chartCards: String    = "",
    val halfPositions: String = ""
)

// ── API client ────────────────────────────────────────────────────────────────

class DashboardApi(private val baseUrl: String, private val client: HttpClient) {

    suspend fun getBalances(token: String): DashboardResult<List<BalanceData>> {
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/banking/balance") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> DashboardResult.Success(response.body())
                HttpStatusCode.Unauthorized -> DashboardResult.Failure("Session expired, please sign in again")
                else                        -> DashboardResult.Failure("Failed to load balances (${response.status.value})")
            }
        } catch (_: Exception) {
            DashboardResult.Failure("Cannot connect to the server")
        }
    }

    suspend fun getTransactions(token: String): DashboardResult<List<TransactionData>> {
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/banking/transactions") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> DashboardResult.Success(response.body())
                HttpStatusCode.Unauthorized -> DashboardResult.Failure("Session expired, please sign in again")
                else                        -> DashboardResult.Failure("Failed to load transactions (${response.status.value})")
            }
        } catch (_: Exception) {
            DashboardResult.Failure("Cannot connect to the server")
        }
    }

    suspend fun getAccounts(token: String): DashboardResult<List<AccountData>> {
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/banking/accounts") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> DashboardResult.Success(response.body())
                HttpStatusCode.Unauthorized -> DashboardResult.Failure("Session expired, please sign in again")
                else                        -> DashboardResult.Failure("Failed to load accounts (${response.status.value})")
            }
        } catch (_: Exception) {
            DashboardResult.Failure("Cannot connect to the server")
        }
    }

    /**
     * Loads the user's saved dashboard layout from the backend.
     * Returns null if none has been saved yet (404) or if the request fails —
     * callers should fall back to local Settings in that case.
     */
    suspend fun loadLayout(token: String): DashboardLayoutDto? {
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/dashboard/layout") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK       -> response.body()
                HttpStatusCode.NotFound -> null   // no layout saved yet — use local defaults
                else                    -> null
            }
        } catch (_: Exception) {
            null   // offline or error — caller falls back to local Settings
        }
    }

    /**
     * Pushes the current dashboard layout to the backend so other platforms
     * (web, desktop, another phone) pick it up on their next load.
     * Fire-and-forget — failures are swallowed; local Settings already has the state.
     */
    suspend fun saveLayout(token: String, layout: DashboardLayoutDto) {
        try {
            client.put("${baseUrl.trimEnd('/')}/api/dashboard/layout") {
                bearerAuth(token)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(layout)
            }
        } catch (_: Exception) {
            // Swallow — local Settings is the fallback; sync will succeed next time
        }
    }
}