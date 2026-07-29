package com.smart_finance_app.budget

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Models ────────────────────────────────────────────────────────────────────

@Serializable
data class BudgetData(
    val id: String,
    val category: String,
    val amount: Double,
    val period: String,   // "monthly" or "weekly"
    val createdAt: String
)

@Serializable
data class BudgetRequest(
    val category: String,
    val amount: Double,
    val period: String
)

sealed interface BudgetResult<out T> {
    data class Success<T>(val data: T) : BudgetResult<T>
    data class Failure(val message: String) : BudgetResult<Nothing>
}

private suspend inline fun <reified T> parseError(response: io.ktor.client.statement.HttpResponse, fallback: String): String {
    return try {
        val errObj = response.body<ErrorMessage>()
        errObj.message.ifBlank { fallback }
    } catch (e: Exception) {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        if (text.isNotBlank()) "HTTP ${response.status.value}: $text" else "$fallback (HTTP ${response.status.value})"
    }
}

// ── API client ────────────────────────────────────────────────────────────────

class BudgetApi(private val baseUrl: String) {

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getBudgets(token: String): BudgetResult<List<BudgetData>> {
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/budgets") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> BudgetResult.Success(response.body())
                HttpStatusCode.Unauthorized -> BudgetResult.Failure("Session expired")
                else                        -> BudgetResult.Failure(parseError<List<BudgetData>>(response, "Failed to load budgets"))
            }
        } catch (e: Exception) {
            BudgetResult.Failure("Cannot connect to server: ${e.message}")
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
                HttpStatusCode.Unauthorized               -> BudgetResult.Failure("Session expired")
                else                                      -> BudgetResult.Failure(parseError<BudgetData>(response, "Failed to create budget"))
            }
        } catch (e: Exception) {
            BudgetResult.Failure("Cannot connect to server: ${e.message}")
        }
    }

    suspend fun updateBudget(token: String, id: String, newAmount: Double, category: String, period: String): BudgetResult<Unit> {
        return try {
            val response = client.put("${baseUrl.trimEnd('/')}/api/budgets/$id") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(BudgetRequest(category = category, amount = newAmount, period = period))
            }
            when (response.status) {
                HttpStatusCode.OK           -> BudgetResult.Success(Unit)
                HttpStatusCode.Unauthorized -> BudgetResult.Failure("Session expired")
                else                        -> BudgetResult.Failure(parseError<Unit>(response, "Failed to update budget"))
            }
        } catch (e: Exception) {
            BudgetResult.Failure("Cannot connect to server: ${e.message}")
        }
    }

    suspend fun deleteBudget(token: String, id: String): BudgetResult<Unit> {
        return try {
            val response = client.delete("${baseUrl.trimEnd('/')}/api/budgets/$id") {
                bearerAuth(token)
            }
            when (response.status) {
                HttpStatusCode.OK           -> BudgetResult.Success(Unit)
                HttpStatusCode.Unauthorized -> BudgetResult.Failure("Session expired")
                else                        -> BudgetResult.Failure(parseError<Unit>(response, "Failed to delete budget"))
            }
        } catch (e: Exception) {
            BudgetResult.Failure("Cannot connect to server: ${e.message}")
        }
    }

    fun close() = client.close()
}

@Serializable
private data class ErrorMessage(val message: String)