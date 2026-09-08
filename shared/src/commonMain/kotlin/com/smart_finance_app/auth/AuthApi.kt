package com.smart_finance_app.auth

import com.smart_finance_app.signin.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class RefreshTokenRequest(val refreshToken: String)

@Serializable
private data class LogoutRequest(val refreshToken: String)

sealed interface RefreshSessionResult {
    data class Success(val session: AuthSession) : RefreshSessionResult
    data object Expired : RefreshSessionResult
    data class Failure(val message: String) : RefreshSessionResult
}

class AuthApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun refresh(refreshToken: String? = null): RefreshSessionResult {
        return try {
            val response = client.post("$normalizedBaseUrl/auth/refresh") {
                if (!refreshToken.isNullOrBlank()) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> RefreshSessionResult.Success(response.body())
                HttpStatusCode.Unauthorized -> RefreshSessionResult.Expired
                else -> RefreshSessionResult.Failure("Could not refresh session. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            RefreshSessionResult.Failure("Cannot connect to the server")
        }
    }

    suspend fun logout(refreshToken: String? = null) {
        runCatching {
            client.post("$normalizedBaseUrl/auth/logout") {
                if (!refreshToken.isNullOrBlank()) {
                    contentType(ContentType.Application.Json)
                    setBody(LogoutRequest(refreshToken))
                }
            }
        }
    }
}