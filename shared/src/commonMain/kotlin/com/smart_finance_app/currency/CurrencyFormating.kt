package com.smart_finance_app.currency

fun getCurrencySymbol(currency: String): String = when (currency.uppercase()) {
    "GBP" -> "£"
    "EUR" -> "€"
    "USD" -> "$"
    "PLN" -> "z\u0142"
    "TWD" -> "NT$"
    else -> currency
}