package com.smart_finance_app.signin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class PasswordResetValidateRequest(val token: String)
@Serializable
private data class PasswordResetRequest(val email: String)

@Serializable
private data class PasswordResetConfirmRequest(val token: String, val newPassword: String)

@Serializable
private data class PasswordResetErrorResponse(val message: String)

sealed interface PasswordResetValidateResult {
    data object Success : PasswordResetValidateResult
    data class Failure(val message: String) : PasswordResetValidateResult
}

sealed interface PasswordResetRequestResult {
    data object Success : PasswordResetRequestResult
    data class Failure(val message: String): PasswordResetRequestResult
}

sealed interface PasswordResetConfirmResult {
    data object Success : PasswordResetConfirmResult
    data class Failure(val message: String) : PasswordResetConfirmResult
}

class PasswordResetApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun validateResetToken(token: String): PasswordResetValidateResult {
        return try {
            val response = client.post("$normalizedBaseUrl/auth/password-reset/validate") {
                contentType(ContentType.Application.Json)
                setBody(PasswordResetValidateRequest(token))
            }

            when (response.status) {
                HttpStatusCode.OK -> PasswordResetValidateResult.Success
                else -> PasswordResetValidateResult.Failure("Reset link is invalid or expired")
            }
        } catch (_: Exception) {
            PasswordResetValidateResult.Failure("Cannot connect to the server")
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

    suspend fun confirmReset(token: String, newPassword: String): PasswordResetConfirmResult {
        return try {
            val response = client.post("$normalizedBaseUrl/auth/password-reset/confirm") {
                contentType(ContentType.Application.Json)
                setBody(
                    PasswordResetConfirmRequest(
                        token = token, newPassword = newPassword
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> PasswordResetConfirmResult.Success
                HttpStatusCode.BadRequest -> PasswordResetConfirmResult.Failure(
                    response.errorMessage("Reset link is invalid or expired.")
                )
                else -> PasswordResetConfirmResult.Failure(
                    response.errorMessage("Password reset failed (${response.status.value})")
                )
            }
        } catch (_: Exception) {
            PasswordResetConfirmResult.Failure("Cannot connect to the server.")
        }
    }

    private suspend fun HttpResponse.errorMessage(fallback: String): String {
        return try {
            body<PasswordResetErrorResponse>().message
        } catch (_: Exception) {
            fallback
        }
    }
}