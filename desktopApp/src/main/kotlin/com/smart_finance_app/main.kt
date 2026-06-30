package com.smart_finance_app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    val apiBaseUrl =
        System.getenv("API_BASE_URL")
            ?: "http://localhost:8080"

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Smart Finance"
        ) {
            App(apiBaseUrl = apiBaseUrl)
        }
    }
}