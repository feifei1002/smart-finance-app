package com.smart_finance_app.consent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface ConsentResult {
    data object Success : ConsentResult
    data class Failure(val message: String) : ConsentResult
}

@Serializable
private data class ErrorResponse(val message: String)

class ConsentApi(baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json { ignoreUnknownKeys = true }
            )
        }
    }

    suspend fun acceptConsent(token: String): ConsentResult {
        val url = "$normalizedBaseUrl/auth/consent"
        println("Calling consent URL: $url")
        return try {
            val response = client.post(url) {
                bearerAuth(token)
            }
            println("Consent response status: ${response.status}")

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> ConsentResult.Success
                HttpStatusCode.Unauthorized -> {
                    ConsentResult.Failure("Your session expired. Status: 401")
                }
                else -> {
                    ConsentResult.Failure(
                        response.errorMessage("Could not save your consent. Status: ${response.status.value}")
                    )
                }
            }
        } catch (_: Exception) {
            ConsentResult.Failure("Cannot connect to the server")
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun HttpResponse.errorMessage(fallback: String): String {
        return try {
            body<ErrorResponse>().message
        } catch (_: Exception) {
            fallback
        }
    }
}