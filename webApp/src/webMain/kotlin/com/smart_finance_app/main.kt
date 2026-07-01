package com.smart_finance_app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val location = window.location
    val apiBaseUrl = "${location.protocol}//${location.hostname}:8080"
    ComposeViewport {
        App(apiBaseUrl = apiBaseUrl)
    }
}