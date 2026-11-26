package com.smart_finance_app.transactions.manual

import com.smart_finance_app.StringKey
import com.smart_finance_app.transactions.ImportedTransactionResponse
import com.smart_finance_app.transactions.PaginatedTransactionsResponse
import com.smart_finance_app.transactions.TransactionsResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.coroutines.cancellation.CancellationException

sealed interface CreateManualTransactionResult {
    data class Success(val transaction: ImportedTransactionResponse) : CreateManualTransactionResult
    data class Failure(val message: StringKey) : CreateManualTransactionResult
    data object Conflict : CreateManualTransactionResult
}

class ManualTransactionsApi(baseUrl: String, private val client: HttpClient) {
    private val url = baseUrl.trimEnd('/') + "/api/transactions"

    suspend fun getTransactions(
        token: String,
        page: Int,
        pageSize: Int,
        type: String
    ): TransactionsResult {
        if (token.isBlank()) return TransactionsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
        return try {
            val response = client.get(url) {
                bearerAuth(token)
                parameter("page", page)
                parameter("pageSize", pageSize.coerceIn(1, 100))
                if (type != "All") parameter("type", type.lowercase())
            }
            when (response.status) {
                HttpStatusCode.OK ->
                    TransactionsResult.Success(response.body<PaginatedTransactionsResponse>())
                HttpStatusCode.Unauthorized ->
                    TransactionsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else -> TransactionsResult.Failure(StringKey.TRANSACTIONS_ERROR_LOAD_FAILED)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ResponseException) {
            TransactionsResult.Failure(
                if (exception.response.status == HttpStatusCode.Unauthorized) {
                    StringKey.COMMON_SESSION_EXPIRED
                } else {
                    StringKey.TRANSACTIONS_ERROR_LOAD_FAILED
                }
            )
        } catch (_: Exception) {
            TransactionsResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun createTransaction(
        token: String,
        request: CreateManualTransactionRequest,
        requestId: String
    ): CreateManualTransactionResult {
        if (token.isBlank()) {
            return CreateManualTransactionResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
        }
        return try {
            val response = client.post(url) {
                bearerAuth(token)
                header("Idempotency-Key", requestId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK ->
                    CreateManualTransactionResult.Success(response.body())
                HttpStatusCode.Conflict -> CreateManualTransactionResult.Conflict
                else -> CreateManualTransactionResult.Failure(createFailureKey(response.status))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ResponseException) {
            if (exception.response.status == HttpStatusCode.Conflict) {
                CreateManualTransactionResult.Conflict
            } else {
                CreateManualTransactionResult.Failure(createFailureKey(exception.response.status))
            }
        } catch (_: Exception) {
            CreateManualTransactionResult.Failure(StringKey.MANUAL_TRANSACTIONS_CREATE_ERROR)
        }
    }

    private fun createFailureKey(status: HttpStatusCode): StringKey = when (status) {
        HttpStatusCode.Unauthorized -> StringKey.COMMON_SESSION_EXPIRED
        HttpStatusCode.NotFound -> StringKey.MANUAL_TRANSACTIONS_ACCOUNT_NOT_FOUND
        HttpStatusCode.BadRequest, HttpStatusCode.UnprocessableEntity ->
            StringKey.MANUAL_TRANSACTIONS_VALIDATION_ERROR
        else -> StringKey.MANUAL_TRANSACTIONS_CREATE_ERROR
    }
}