package com.smart_finance_app.transactions

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ImportedTransactionResponse(
    val id: String,
    val date: String,
    val merchantName: String,
    val category: String,
    val accountName: String,
    val amount: Double,
    val currency: String
)

@Serializable
data class TransactionSyncResponse(
    val importedCount: Int,
    val duplicateCount: Int,
    val lastSuccessfulSyncAt: String?
)

sealed interface TransactionsResult {
    data class Success(val transactions: List<ImportedTransactionResponse>): TransactionsResult
    data class Failure(val message: String): TransactionsResult
}

sealed interface TransactionSyncResult {
    data class Success(val result: TransactionSyncResponse): TransactionSyncResult
    data class Failure(val message: String): TransactionSyncResult
}

class TransactionsApi(baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun syncTransactions(token: String): TransactionSyncResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/banking/transactions/sync") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> TransactionSyncResult.Success(response.body())
                HttpStatusCode.Unauthorized -> TransactionSyncResult.Failure("Your session expired. Please sign in again.")
                else -> TransactionSyncResult.Failure("Could not sync transactions. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            TransactionSyncResult.Failure("Cannot connect to the server.")
        }
    }

    suspend fun getTransactions(token: String): TransactionsResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/banking/transactions/imported") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> TransactionsResult.Success(response.body<List<ImportedTransactionResponse>>())
                HttpStatusCode.Unauthorized -> TransactionsResult.Failure("Your session expired. Please sign in again.")
                else -> TransactionsResult.Failure("Could not load transactions. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            TransactionsResult.Failure("Cannot connect to the server.")
        }
    }

    fun close() {
        client.close()
    }
}