package com.smart_finance_app.transactions

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class ImportedTransactionResponse(
    val id: String,
    val date: String,
    val merchantName: String,
    val category: String,
    val accountName: String,
    val accountId: String? = null,
    val amount: Double,
    val currency: String,
    val merchantLogoUrl: String? = null
)


@Serializable
data class TransactionSyncResponse(
    val importedCount: Int,
    val duplicateCount: Int,
    val lastSuccessfulSyncAt: String?
)

@Serializable
data class PaginatedTransactionsResponse(
    val transactions: List<ImportedTransactionResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasMore: Boolean
)

sealed interface TransactionsResult {
    data class Success(val page: PaginatedTransactionsResponse): TransactionsResult
    data class Failure(val message: StringKey): TransactionsResult
}

sealed interface TransactionSyncResult {
    data class Success(val result: TransactionSyncResponse): TransactionSyncResult
    data class Failure(val message: StringKey): TransactionSyncResult
}

class TransactionsApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun syncTransactions(token: String): TransactionSyncResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/banking/transactions/sync") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> TransactionSyncResult.Success(response.body<TransactionSyncResponse>())
                HttpStatusCode.Unauthorized -> TransactionSyncResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else -> TransactionSyncResult.Failure(StringKey.TRANSACTIONS_ERROR_SYNC_FAILED)
            }
        } catch (_: Exception) {
            TransactionSyncResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun getTransactions(token: String, page: Int, pageSize: Int, type: String): TransactionsResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/banking/transactions/imported") {
                bearerAuth(token)
                parameter("page", page)
                parameter("pageSize", pageSize)

                if (type != "All") {
                    parameter("type", type.lowercase())
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> TransactionsResult.Success(response.body<PaginatedTransactionsResponse>())
                HttpStatusCode.Unauthorized -> TransactionsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else -> TransactionsResult.Failure(StringKey.TRANSACTIONS_ERROR_LOAD_FAILED)
            }
        } catch (_: Exception) {
            TransactionsResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}