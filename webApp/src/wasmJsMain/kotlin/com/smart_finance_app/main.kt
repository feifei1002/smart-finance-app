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
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.json.Json
import web.http.RequestCredentials
import web.http.include
import web.fonts.FontFace
import web.fonts.FontFaceDescriptors

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

    // Load Noto Sans TC font so Traditional Chinese renders correctly
    // in the WASM Canvas renderer
    loadNotoSansTCFont {
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

@JsFun("""
function(onComplete) {
    var fontUrl = 'https://fonts.gstatic.com/s/notosanstc/v35/nKKF-GM_FYFRJvXzVXaAPe97P1KHynJFKFhLNEiLDzA.woff2';
    var font = new FontFace('Noto Sans TC', 'url(' + fontUrl + ')');
    font.load().then(function(loadedFont) {
        document.fonts.add(loadedFont);
        onComplete();
    }).catch(function(err) {
        console.warn('Noto Sans TC font failed to load:', err);
        onComplete();
    });
}
""")
external fun loadNotoSansTCFont(onComplete: () -> Unit)
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