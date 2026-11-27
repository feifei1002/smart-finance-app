package com.smart_finance_app.server.manual

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

internal class ManualTransactionValidationException(message: String) :
    IllegalArgumentException(message)

private val positiveMoneyPattern = Regex("[0-9]{1,12}(?:\\.[0-9]{1,2})?")
private val isoDatePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val earliestTransactionDate: LocalDate = LocalDate.of(1900, 1, 1)

internal fun validateManualTransactionRequest(
    request: CreateManualTransactionRequest,
    today: LocalDate = LocalDate.now(ZoneOffset.UTC)
): ValidatedManualTransaction {
    val accountId = try {
        UUID.fromString(request.accountId.trim()).also {
            require(it.toString().equals(request.accountId.trim(), ignoreCase = true))
        }
    } catch (_: IllegalArgumentException) {
        throw ManualTransactionValidationException("Select a valid account")
    }

    val amountText = request.amount.trim()
    if (!positiveMoneyPattern.matches(amountText)) {
        throw ManualTransactionValidationException(
            "Amount must be positive with up to 12 whole digits and 2 decimal places"
        )
    }
    val amount = BigDecimal(amountText).setScale(2, RoundingMode.UNNECESSARY)
    if (amount.signum() <= 0) {
        throw ManualTransactionValidationException("Amount must be greater than zero")
    }

    val description = request.description.trim()
    if (description.isEmpty()) {
        throw ManualTransactionValidationException("Merchant or description is required")
    }
    if (description.length > 100) {
        throw ManualTransactionValidationException(
            "Merchant or description must be at most 100 characters"
        )
    }

    if (request.category !in ManualTransactionCategories.forType(request.type)) {
        throw ManualTransactionValidationException("Unsupported category for this transaction type")
    }

    val dateText = request.date.trim()
    val date = try {
        if (!isoDatePattern.matches(dateText)) throw DateTimeParseException("", dateText, 0)
        LocalDate.parse(dateText)
    } catch (_: DateTimeParseException) {
        throw ManualTransactionValidationException("Date must be in YYYY-MM-DD format")
    }
    // One day of slack: the client validates against the user's local date, which can be
    // ahead of UTC.
    if (date.isBefore(earliestTransactionDate) || date.isAfter(today.plusDays(1))) {
        throw ManualTransactionValidationException("Date cannot be in the future")
    }

    val signedAmount = if (request.type == ManualTransactionType.EXPENSE) amount.negate() else amount
    return ValidatedManualTransaction(
        accountId = accountId,
        type = request.type,
        signedAmount = signedAmount,
        description = description,
        category = request.category,
        date = date
    )
}

internal fun parseManualTransactionPage(raw: String?): Int {
    if (raw == null) return 0
    val page = raw.toIntOrNull()
    require(page != null && page in 0..100_000) { "page must be between 0 and 100000" }
    return page
}

internal fun parseManualTransactionPageSize(raw: String?): Int {
    if (raw == null) return 25
    val size = raw.toIntOrNull()
    require(size != null && size in 1..100) { "pageSize must be between 1 and 100" }
    return size
}

internal fun parseManualTransactionTypeFilter(raw: String?): ManualTransactionType? =
    when (raw?.lowercase()) {
        null, "", "all" -> null
        "income" -> ManualTransactionType.INCOME
        "expense", "expenses" -> ManualTransactionType.EXPENSE
        else -> throw IllegalArgumentException("type must be income or expenses")
    }