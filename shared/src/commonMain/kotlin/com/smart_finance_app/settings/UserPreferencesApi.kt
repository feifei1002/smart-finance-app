package com.smart_finance_app.settings

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class UpdateLanguageRequest(val language: String)

@Serializable
private data class UpdateLanguageResponse(val language: String)

sealed interface UpdateLanguageResult {
    data object Success : UpdateLanguageResult
    data class Failure(val message: String) : UpdateLanguageResult
}

class UserPreferencesApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun updateLanguage(token: String, languageCode: String): UpdateLanguageResult {
        return try {
            val response = client.patch("$normalizedBaseUrl/api/user/preferences/language") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(UpdateLanguageRequest(languageCode))
            }
            when (response.status) {
                HttpStatusCode.OK           -> UpdateLanguageResult.Success
                HttpStatusCode.Unauthorized -> UpdateLanguageResult.Failure("Session expired. Please sign in again.")
                HttpStatusCode.BadRequest   -> UpdateLanguageResult.Failure("Invalid language code.")
                else                        -> UpdateLanguageResult.Failure("Failed to save language preference (${response.status.value})")
            }
        } catch (_: Exception) {
            UpdateLanguageResult.Failure("Cannot connect to the server.")
        }
    }
}