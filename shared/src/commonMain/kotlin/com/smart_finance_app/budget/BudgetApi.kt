package com.smart_finance_app.budget

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

// ── Models ────────────────────────────────────────────────────────────────────

@Serializable
data class BudgetData(
    val id: String,
    val category: String,
    val amount: Double,
    val period: String,
    val currency: String = "GBP",   // ← add this
    val createdAt: String
)

@Serializable
data class BudgetRequest(
    val category: String,
    val amount: Double,
    val period: String,
    val currency: String = "GBP"    // ← add this
)

sealed interface BudgetResult<out T> {
    data class Success<T>(val data: T) : BudgetResult<T>
    data class Failure(val message: StringKey) : BudgetResult<Nothing>
}

// ── API client ────────────────────────────────────────────────────────────────

class BudgetApi(private val baseUrl: String, private val client: HttpClient) {

    suspend fun getBudgets(token: String): BudgetResult<List<BudgetData>> {
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/budgets") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> BudgetResult.Success(response.body())
                HttpStatusCode.Unauthorized -> BudgetResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else                        -> BudgetResult.Failure(StringKey.BUDGET_ERROR_LOAD_FAILED)
            }
        } catch (_: Exception) {
            BudgetResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun createBudget(token: String, request: BudgetRequest): BudgetResult<BudgetData> {
        return try {
            val response = client.post("${baseUrl.trimEnd('/')}/api/budgets") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK -> BudgetResult.Success(response.body())
                HttpStatusCode.Unauthorized               -> BudgetResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else                                      -> BudgetResult.Failure(StringKey.BUDGET_ERROR_CREATE_FAILED)
            }
        } catch (_: Exception) {
            BudgetResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun updateBudget(
        token: String,
        id: String,
        newAmount: Double,
        category: String,
        period: String,
        currency: String = "GBP"    // ← add this
    ): BudgetResult<Unit> {
        return try {
            val response = client.put("${baseUrl.trimEnd('/')}/api/budgets/$id") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(BudgetRequest(
                    category = category,
                    amount   = newAmount,
                    period   = period,
                    currency = currency   // ← add this
                ))
            }
            when (response.status) {
                HttpStatusCode.OK           -> BudgetResult.Success(Unit)
                HttpStatusCode.Unauthorized -> BudgetResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else                        -> BudgetResult.Failure(StringKey.BUDGET_ERROR_UPDATE_FAILED)
            }
        } catch (_: Exception) {
            BudgetResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun deleteBudget(token: String, id: String): BudgetResult<Unit> {
        return try {
            val response = client.delete("${baseUrl.trimEnd('/')}/api/budgets/$id") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> BudgetResult.Success(Unit)
                HttpStatusCode.Unauthorized -> BudgetResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else                        -> BudgetResult.Failure(StringKey.BUDGET_ERROR_DELETE_FAILED)
            }
        } catch (_: Exception) {
            BudgetResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}