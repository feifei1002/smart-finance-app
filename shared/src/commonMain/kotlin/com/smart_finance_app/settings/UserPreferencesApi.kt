package com.smart_finance_app.settings

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class UpdateLanguageRequest(val language: String)

sealed interface UpdateLanguageResult {
    data object Success : UpdateLanguageResult
    data class Failure(val message: StringKey) : UpdateLanguageResult
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
                HttpStatusCode.Unauthorized -> UpdateLanguageResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                HttpStatusCode.BadRequest   -> UpdateLanguageResult.Failure(StringKey.SETTINGS_LANGUAGE_SAVE_FAILED)
                else                        -> UpdateLanguageResult.Failure(StringKey.SETTINGS_LANGUAGE_SAVE_FAILED)
            }
        } catch (_: Exception) {
            UpdateLanguageResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}