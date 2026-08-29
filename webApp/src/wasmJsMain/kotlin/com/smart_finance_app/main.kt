package com.smart_finance_app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.smart_finance_app.auth.WebTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.fetchOptions
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import web.http.RequestCredentials
import web.http.include

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalWasmJsInterop::class
)
fun main() {
    val location = window.location
    val apiBaseUrl = "${location.protocol}//${location.hostname}:8080"

    val httpClient = HttpClient(Js) {
        expectSuccess = false

        engine {
            configureRequest {
                credentials = RequestCredentials.include
            }
        }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    ComposeViewport {
        App(
            apiBaseUrl = apiBaseUrl,
            tokenStorage = WebTokenStorage(),
            httpClient = httpClient,
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