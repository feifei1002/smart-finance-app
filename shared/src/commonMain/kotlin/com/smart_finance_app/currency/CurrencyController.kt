package com.smart_finance_app.currency

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings

object CurrencyController {

    private val settings = Settings()
    private const val KEY = "app_currency"

    val supportedCurrencies = listOf("GBP", "USD", "EUR","PLN", "TWD")

    var currentCurrency by mutableStateOf(
        settings.getStringOrNull(KEY) ?: "GBP"
    )
        private set

    fun setCurrency(code: String) {
        val safe = if (code in supportedCurrencies) code else "GBP"
        currentCurrency = safe
        settings.putString(KEY, safe)
    }
}