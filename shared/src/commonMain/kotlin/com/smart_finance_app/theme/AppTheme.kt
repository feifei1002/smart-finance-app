package com.smart_finance_app.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.smart_finance_app.StringKey

enum class AppTheme(
    val colour: String,
    val titleKey: StringKey
) {
    PastelBlue("pastel_blue", StringKey.THEME_PASTEL_BLUE),
    PastelPurple("pastel_purple", StringKey.THEME_PASTEL_PURPLE),
    PastelGreen("pastel_green", StringKey.THEME_PASTEL_GREEN);

    companion object {
        val Default = PastelGreen

        fun fromColour(colour: String?): AppTheme =
            entries.firstOrNull { it.colour == colour } ?: Default
    }
}

object ThemeController {
    private val settings = Settings()
    private const val KEY = "app_theme"

    var currentTheme by mutableStateOf(AppTheme.fromColour(settings.getStringOrNull(KEY)))
        private set

    fun setTheme(theme: AppTheme) {
        currentTheme = theme
        settings.putString(KEY, theme.colour)
    }

    fun setTheme(colour: String?) {
        setTheme(AppTheme.fromColour(colour))
    }
}