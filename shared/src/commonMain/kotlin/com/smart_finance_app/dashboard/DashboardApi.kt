package com.smart_finance_app.dashboard

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val merchantName: String? = null
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

// ── API client ────────────────────────────────────────────────────────────────

class DashboardApi(private val baseUrl: String) {

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            DashboardResult.Failure("Cannot connect to the server")
        }
    }

    fun close() = client.close()
}