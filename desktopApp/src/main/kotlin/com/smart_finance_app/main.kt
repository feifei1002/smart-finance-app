package com.smart_finance_app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.smart_finance_app.auth.DesktopTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun main() {
    val apiBaseUrl =
        System.getenv("API_BASE_URL")
            ?: "http://localhost:8080"

    val httpClient = HttpClient(CIO) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Smart Finance"
        ) {
            App(
                apiBaseUrl = apiBaseUrl,
                tokenStorage = DesktopTokenStorage(),
                httpClient = httpClient
                )
        }
    }
}