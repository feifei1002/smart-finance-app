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

@Serializable
private data class UpdateCurrencyRequest(val currency: String)

sealed interface UpdateLanguageResult {
    data object Success : UpdateLanguageResult
    data class Failure(val message: StringKey) : UpdateLanguageResult
}

sealed interface UpdateCurrencyResult {
    data object Success : UpdateCurrencyResult
    data class Failure(val message: StringKey) : UpdateCurrencyResult
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

    suspend fun updateCurrency(token: String, currencyCode: String): UpdateCurrencyResult {
        return try {
            val response = client.patch("$normalizedBaseUrl/api/user/preferences/currency") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(UpdateCurrencyRequest(currencyCode))
            }
            when (response.status) {
                HttpStatusCode.OK           -> UpdateCurrencyResult.Success
                HttpStatusCode.Unauthorized -> UpdateCurrencyResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                HttpStatusCode.BadRequest   -> UpdateCurrencyResult.Failure(StringKey.SETTINGS_CURRENCY_SAVE_FAILED)
                else                        -> UpdateCurrencyResult.Failure(StringKey.SETTINGS_CURRENCY_SAVE_FAILED)
            }
        } catch (_: Exception) {
            UpdateCurrencyResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}