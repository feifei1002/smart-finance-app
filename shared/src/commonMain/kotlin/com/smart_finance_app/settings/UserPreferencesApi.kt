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

@Serializable
private data class UpdateThemeRequest(val theme: String)

@Serializable
private data class UpdatePreferencesRequest(
    val language: String,
    val currency: String,
    val theme: String
)


sealed interface UpdateLanguageResult {
    data object Success : UpdateLanguageResult
    data class Failure(val message: StringKey) : UpdateLanguageResult
}

sealed interface UpdateCurrencyResult {
    data object Success : UpdateCurrencyResult
    data class Failure(val message: StringKey) : UpdateCurrencyResult
}

sealed interface UpdateThemeResult {
    data object Success : UpdateThemeResult
    data class Failure(val message: StringKey) : UpdateThemeResult
}

sealed interface UpdatePreferencesResult {
    data object Success : UpdatePreferencesResult
    data class Failure(val message: StringKey) : UpdatePreferencesResult
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

    suspend fun updateTheme(token: String, themeCode: String): UpdateThemeResult {
        return try {
            val response = client.patch("$normalizedBaseUrl/api/user/preferences/theme") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(UpdateThemeRequest(themeCode))
            }

            when (response.status) {
                HttpStatusCode.OK -> UpdateThemeResult.Success
                HttpStatusCode.Unauthorized -> UpdateThemeResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else -> UpdateThemeResult.Failure(StringKey.SETTINGS_THEME_SAVE_FAILED)
            }
        } catch (_: Exception) {
            UpdateThemeResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun updatePreferences(
        token: String,
        languageCode: String,
        currencyCode: String,
        themeCode: String
    ): UpdatePreferencesResult {
        return try {
            val response = client.patch("$normalizedBaseUrl/api/user/preferences") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    UpdatePreferencesRequest(
                        language = languageCode,
                        currency = currencyCode,
                        theme = themeCode
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> UpdatePreferencesResult.Success
                HttpStatusCode.Unauthorized -> UpdatePreferencesResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else -> UpdatePreferencesResult.Failure(StringKey.PREFERENCES_SAVE_FAILED)
            }
        } catch (_: Exception) {
            UpdatePreferencesResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}