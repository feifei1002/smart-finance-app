package com.smart_finance_app.signin

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

sealed interface PasswordResetValidateResult {
    data object Success : PasswordResetValidateResult
    data class Failure(val message: StringKey) : PasswordResetValidateResult
}

sealed interface PasswordResetRequestResult {
    data object Success : PasswordResetRequestResult
    data class Failure(val message: StringKey): PasswordResetRequestResult
}

sealed interface PasswordResetConfirmResult {
    data object Success : PasswordResetConfirmResult
    data class Failure(val message: StringKey) : PasswordResetConfirmResult
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
                else -> PasswordResetValidateResult.Failure(StringKey.RESET_PASSWORD_ERROR_INVALID_OR_EXPIRED)
            }
        } catch (_: Exception) {
            PasswordResetValidateResult.Failure(StringKey.COMMON_ERROR_SERVER)
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
                HttpStatusCode.BadRequest -> PasswordResetRequestResult.Failure(StringKey.FORGOT_PASSWORD_INVALID_EMAIL)

                HttpStatusCode.ServiceUnavailable -> PasswordResetRequestResult.Failure(StringKey.FORGOT_PASSWORD_ERROR_EMAIL_UNAVAILABLE)

                else -> PasswordResetRequestResult.Failure(StringKey.FORGOT_PASSWORD_ERROR_REQUEST_FAILED)
            }
        } catch (_: Exception) {
            PasswordResetRequestResult.Failure(StringKey.COMMON_ERROR_SERVER)
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
                HttpStatusCode.BadRequest -> PasswordResetConfirmResult.Failure(StringKey.RESET_PASSWORD_ERROR_INVALID_OR_EXPIRED)

                else -> PasswordResetConfirmResult.Failure(StringKey.RESET_PASSWORD_ERROR_CONFIRM_FAILED)
            }
        } catch (_: Exception) {
            PasswordResetConfirmResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}