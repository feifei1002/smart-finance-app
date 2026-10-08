package com.smart_finance_app.server.budget

internal val allowedCurrencies = setOf("GBP", "USD", "EUR", "TWD", "PLN")
private val allowedPeriods = setOf("monthly", "weekly")

internal fun validateBudgetRequest(request: BudgetRequest): String? {
    return when {
        request.category.isBlank() -> "Category is required"
        request.amount <= 0 -> "Amount must be greater than 0"
        request.period !in allowedPeriods -> "Period must be monthly or weekly"
        request.currency !in allowedCurrencies -> "Invalid currency"
        else -> null
    }
}

internal fun isDuplicateBudgetError(exception: Exception): Boolean {
    return exception.message?.contains("unique constraint", ignoreCase = true) == true ||
            exception.message?.contains("duplicate key", ignoreCase = true) == true
}