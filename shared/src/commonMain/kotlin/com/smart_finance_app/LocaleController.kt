package com.smart_finance_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LocaleController {

    val supportedLanguages = listOf(
        Language("en",    "English"),
        Language("es",    "Español"),
        Language("fr",    "Français"),
        Language("nl",    "Nederlands"),
        Language("de",    "Deutsch"),
        Language("it",    "Italiano"),
        Language("pl",    "Polski"),
        Language("zh-TW", "繁體中文")
    )

    var currentLanguageCode by mutableStateOf("en")
        private set

    fun setLanguage(code: String) {
        val safeCode = if (supportedLanguages.any { it.code == code }) code else "en"
        currentLanguageCode = safeCode
    }
}

data class Language(
    val code: String,
    val displayName: String
)