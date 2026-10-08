package com.smart_finance_app.server.preferences

private val allowedLanguages  = setOf("en", "es", "fr", "nl", "de", "it", "pl", "zh-TW")
private val allowedCurrencies = setOf("GBP", "USD", "EUR", "PLN", "TWD")
private val allowedThemes = setOf("pastel_blue", "pastel_purple", "pastel_green")

internal fun validateLanguage(language: String): String? {
    return if (language in allowedLanguages) {
        null
    } else {
        "Invalid language code. Must be one of: ${allowedLanguages.joinToString()}"
    }
}

internal fun validateCurrency(currency: String): String? {
    return if (currency in allowedCurrencies) {
        null
    } else {
        "Invalid currency code. Must be one of: ${allowedCurrencies.joinToString()}"
    }
}

internal fun validateTheme(theme: String): String? {
    return if (theme in allowedThemes) null else "Invalid theme"
}

internal fun validatePreferences(request: UpdatePreferencesRequest): String? {
    return validateLanguage(request.language)
        ?: validateCurrency(request.currency)
        ?: validateTheme(request.theme)
}