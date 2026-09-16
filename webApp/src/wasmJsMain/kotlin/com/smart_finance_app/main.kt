package com.smart_finance_app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.smart_finance_app.auth.WebTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.json.Json
import web.http.RequestCredentials
import web.http.include

// Only pass primitive types and callbacks across the WASM/JS boundary
@JsFun("""
function(startApp) {
    document.fonts.ready.then(function() {
        var font = new FontFace(
            'Noto Sans TC',
            "url('NotoSansTC-Regular.ttf') format('truetype')"
        );
        font.load().then(function(loaded) {
            document.fonts.add(loaded);
            startApp();
        }).catch(function(err) {
            console.warn('Font load failed, starting anyway:', err);
            startApp();
        });
    });
}
""")
external fun loadFontThenStart(startApp: () -> Unit)

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalWasmJsInterop::class
)
fun main() {
    val location = window.location
    val apiBaseUrl = "${location.protocol}//${location.hostname}:8080"

    val httpClient = HttpClient(Js) {
        expectSuccess = false
        defaultRequest {
            header("X-Refresh-Token-Transport", "cookie")
        }
        engine {
            configureRequest {
                credentials = RequestCredentials.include
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Build everything in Kotlin, only pass the lambda to JS
    loadFontThenStart {
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
}

private fun isPasswordResetRoute(): Boolean {
    return window.location.hash.startsWith("#/reset-password")
}

private fun passwordResetTokenFromUrl(): String? {
    val hash = window.location.hash
    if (!hash.startsWith("#/reset-password")) return null
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