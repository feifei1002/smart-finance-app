package com.smart_finance_app.server.manual

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

internal class ManualAccountValidationException(message: String) :
    IllegalArgumentException(message)

private val manualAccountCurrencies = setOf("GBP", "USD", "EUR", "PLN", "TWD")
private val canonicalMoneyPattern = Regex("[+-]?[0-9]{1,12}(?:\\.[0-9]{1,2})?")

internal fun validateManualAccountRequest(request: CreateManualAccountRequest): ValidatedManualAccount {
    val name = request.name.trim()
    if (name.isEmpty()) {
        throw ManualAccountValidationException("Account name is required")
    }
    if (name.length > 100) {
        throw ManualAccountValidationException("Account name must be at most 100 characters")
    }

    val currency = request.currency.trim().uppercase(Locale.ROOT)
    if (currency !in manualAccountCurrencies) {
        throw ManualAccountValidationException("Unsupported currency")
    }
    // The frontend sends canonical decimal strings; reject exponent notation and grouping.
    val amountText = request.startingBalance.trim()
    if (!canonicalMoneyPattern.matches(amountText)) {
        throw ManualAccountValidationException(
            "Starting balance must have up to 12 whole digits and 2 decimal places"
        )
    }
    val amount = BigDecimal(amountText).setScale(2, RoundingMode.UNNECESSARY)
    if (amount.signum() < 0 && request.type != ManualAccountType.CREDIT_CARD) {
        throw ManualAccountValidationException(
            "Only credit cards can have a negative starting balance"
        )
    }

    return ValidatedManualAccount(name, request.type, currency, amount)
}