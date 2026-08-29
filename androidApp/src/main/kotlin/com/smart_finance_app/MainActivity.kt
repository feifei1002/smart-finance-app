package com.smart_finance_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smart_finance_app.auth.AndroidTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val apiBaseUrl = getString(R.string.api_base_url)

        val tokenStorage = AndroidTokenStorage(this)

        val httpClient = HttpClient {
            expectSuccess = false

            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        setContent {
            App(
                apiBaseUrl = apiBaseUrl,
                tokenStorage = tokenStorage,
                httpClient = httpClient
            )
        }
    }
}