package com.smart_finance_app.server.preferences

import kotlinx.serialization.Serializable

@Serializable
data class UpdateLanguageRequest(val language: String)

@Serializable
data class UpdateLanguageResponse(val language: String)

@Serializable
data class UpdateCurrencyRequest(val currency: String)

@Serializable
data class UpdateCurrencyResponse(val currency: String)

@Serializable
data class UpdateThemeRequest(val theme: String)

@Serializable
data class UpdateThemeResponse(val theme: String)


@Serializable
data class UpdatePreferencesRequest(
    val language: String,
    val currency: String,
    val theme: String
)

@Serializable
data class UpdatePreferencesResponse(
    val language: String,
    val currency: String,
    val theme: String
)