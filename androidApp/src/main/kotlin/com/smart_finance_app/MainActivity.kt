package com.smart_finance_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val apiBaseUrl = getString(R.string.api_base_url)

        setContent {
            App(apiBaseUrl = apiBaseUrl)
        }
    }
}