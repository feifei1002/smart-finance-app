package com.smart_finance_app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val location = window.location
    val apiBaseUrl = "${location.protocol}//${location.hostname}:8080"
    ComposeViewport {
        App(
            apiBaseUrl = apiBaseUrl,
            isPasswordResetRoute = isPasswordResetRoute(),
            passwordResetToken = passwordResetTokenFromUrl()
        )
    }
}

private fun isPasswordResetRoute(): Boolean {
    return window.location.hash.startsWith("#/reset-password")
}
private fun passwordResetTokenFromUrl(): String? {
    val hash = window.location.hash

    if (!hash.startsWith("#/reset-password")) {
        return null
    }

    val query = hash.substringAfter("?", missingDelimiterValue = "")
    if (query.isBlank()) return null

    return query
        .split("&")
        .mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size == 2) pieces[0] to pieces[1] else null
        }
        .firstOrNull { it.first == "token" }
        ?.second
}