package com.smart_finance_app.signin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PasswordResetRequest(val email: String)

@Serializable
private data class PasswordResetErrorResponse(val message: String)

sealed interface PasswordResetRequestResult {
    data object Success : PasswordResetRequestResult
    data class Failure(val message: String): PasswordResetRequestResult
}

class PasswordResetApi(baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun requestReset(email: String): PasswordResetRequestResult {
        return try {
            val response = client.post("$normalizedBaseUrl/auth/password-reset/request") {
                contentType(ContentType.Application.Json)
                setBody(PasswordResetRequest(email.trim().lowercase()))
            }

            when (response.status) {
                HttpStatusCode.OK -> PasswordResetRequestResult.Success
                HttpStatusCode.BadRequest -> PasswordResetRequestResult.Failure(
                    response.errorMessage("Please enter a valid email address.")
                )

                HttpStatusCode.ServiceUnavailable -> PasswordResetRequestResult.Failure(
                    response.errorMessage("Password reset email could not be sent. Please try again later.")
                )
                else -> PasswordResetRequestResult.Failure(
                    response.errorMessage("Password reset request failed (${response.status.value})")
                )
            }
        } catch (_: Exception) {
            PasswordResetRequestResult.Failure("Cannot connect to the server.")
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun HttpResponse.errorMessage(fallback: String): String {
        return try {
            body<PasswordResetErrorResponse>().message
        } catch (_: Exception) {
            fallback
        }
    }
}