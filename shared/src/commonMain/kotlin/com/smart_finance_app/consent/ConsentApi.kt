package com.smart_finance_app.consent

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

sealed interface ConsentResult {
    data object Success : ConsentResult
    data class Failure(val message: StringKey) : ConsentResult
}

class ConsentApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

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
                    ConsentResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }
                else -> {
                    ConsentResult.Failure(StringKey.CONSENT_ERROR_SAVE_FAILED)
                }
            }
        } catch (_: Exception) {
            ConsentResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}