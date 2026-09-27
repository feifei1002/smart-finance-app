package com.smart_finance_app.dashboard

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal fun currentMonthIncomeAndExpenses(
    transactions: List<TransactionData>,
    displayCurrency: String,
    rates: Map<String, Double>
): Pair<Double, Double> {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val thisMonth = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        parts.size == 3 &&
                parts[0].toIntOrNull() == now.year &&
                parts[1].toIntOrNull() == now.month.number
    }

    val income = thisMonth.filter { it.amount > 0 }
        .sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }

    val expenses = thisMonth.filter { it.amount < 0 }
        .sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }

    return income to expenses
}