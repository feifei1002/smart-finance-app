package com.smart_finance_app.dashboard

import androidx.compose.ui.graphics.Color
import com.smart_finance_app.currency.ConversionResult
import com.smart_finance_app.currency.ExchangeRateService
import com.smart_finance_app.currency.getCurrencySymbol
import com.smart_finance_app.transactions.TransactionCategories
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

data class MonthlyTopCategory(
    val month: String,
    val category: String,
    val amount: Float,
    val color: Color
)

data class DashboardState(
    val currentBalance: Double,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val currency: String,
    val spendingCategories: List<SpendingCategory>,
    val monthlyTrend: List<MonthlyPoint>,
    val recentTransactions: List<Transaction>,
    val accounts: List<AccountOverview>,
    val monthlyTopCategories: List<MonthlyTopCategory>,
    val rawTransactions: List<TransactionData>,
    val balanceChangePercent: Float,
    val incomeChangePercent: Float,
    val expenseChangePercent: Float
)

private val categoryColors = listOf(
    Color(0xFF22C55E),
    Color(0xFFEC4899),
    Color(0xFF2563EB),
    Color(0xFFF97316),
    Color(0xFF06B6D4),
    Color(0xFF8B5CF6),
    Color(0xFF14B8A6),
    Color(0xFFEF4444),
)

private val categoryNames = TransactionCategories.all


fun formatCurrency(value: Double, symbol: String, currencyCode: String = ""): String {
    val absValue = kotlin.math.abs(value)
    val prefix   = if (value < 0) "-" else ""
    val zeroDecimalCurrencies = setOf("TWD", "JPY", "KRW")
    return if (currencyCode.uppercase() in zeroDecimalCurrencies) {
        val rounded = kotlin.math.round(absValue).toLong()
        "$prefix$symbol$rounded"
    } else {
        val intPart = absValue.toLong()
        val decPart = kotlin.math.round((absValue - intPart) * 100).toLong()
        "$prefix$symbol$intPart.${decPart.toString().padStart(2, '0')}"
    }
}

fun formatDate(timestamp: String): String {
    return try {
        val date = timestamp.take(10).split("-")
        val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        "${months[date[1].toInt()]} ${date[2].toInt()}, ${date[0]}"
    } catch (_: Exception) {
        timestamp.take(10)
    }
}

enum class SpendingPeriod(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year")
}

/**
 * Converts a transaction amount to the display currency.
 * Returns null if conversion fails — callers must handle this explicitly
 * to avoid silently mixing currencies.
 */
private fun TransactionData.convertedAmount(
    displayCurrency: String,
    rates: Map<String, Double>
): Double? = when (val result = ExchangeRateService.convert(
    amount = this.amount,
    fromCurrency = this.currency,
    toCurrency = displayCurrency,
    rates = rates
)) {
    is ConversionResult.Success -> result.amount
    ConversionResult.RatesUnavailable -> null
    is ConversionResult.MissingCurrency -> null
}

/**
 * Converts a balance amount to the display currency.
 * Returns null if conversion fails — shown as "--" in UI rather than wrong value.
 */
private fun convertBalance(
    amount: Double,
    fromCurrency: String,
    displayCurrency: String,
    rates: Map<String, Double>
): Double? = when (val result = ExchangeRateService.convert(
    amount = amount,
    fromCurrency = fromCurrency,
    toCurrency = displayCurrency,
    rates = rates
)) {
    is ConversionResult.Success -> result.amount
    ConversionResult.RatesUnavailable -> null
    is ConversionResult.MissingCurrency -> null
}

fun computeSpendingCategories(
    transactions: List<TransactionData>,
    period: SpendingPeriod,
    displayCurrency: String,
    rates: Map<String, Double>
): List<SpendingCategory> {
    val symbol = getCurrencySymbol(displayCurrency)
    val now    = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    val filtered = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        if (parts.size != 3) return@filter false
        val txYear  = parts[0].toIntOrNull() ?: return@filter false
        val txMonth = parts[1].toIntOrNull() ?: return@filter false
        @Suppress("UNUSED_VARIABLE")
        val txDay   = parts[2].toIntOrNull() ?: return@filter false
        when (period) {
            SpendingPeriod.THIS_MONTH -> txYear == now.year && txMonth == now.month.number
            SpendingPeriod.LAST_MONTH -> {
                val last = now.date.minus(DatePeriod(months = 1))
                txYear == last.year && txMonth == last.month.number
            }
            SpendingPeriod.LAST_3_MONTHS -> {
                val cutoff = now.date.minus(DatePeriod(months = 3))
                (txYear > cutoff.year) || (txYear == cutoff.year && txMonth >= cutoff.month.number)
            }
            SpendingPeriod.THIS_YEAR -> txYear == now.year
        }
    }.filter { it.amount < 0 }

    val totalSpend = filtered.sumOf { tx ->
        val converted = tx.convertedAmount(displayCurrency, rates)
        if (converted != null) kotlin.math.abs(converted) else 0.0
    }.takeIf { it > 0 } ?: 1.0

    val colorMap = categoryNames.zip(categoryColors).toMap()

    return filtered
        .groupBy { TransactionCategories.normalize(it.category) }
        .entries
        .sortedByDescending { entry ->
            entry.value.sumOf { tx ->
                val converted = tx.convertedAmount(displayCurrency, rates)
                if (converted != null) kotlin.math.abs(converted) else 0.0
            }
        }
        .mapIndexed { index, (category, txList) ->
            val absAmount = txList.sumOf { tx ->
                val converted = tx.convertedAmount(displayCurrency, rates)
                if (converted != null) kotlin.math.abs(converted) else 0.0
            }
            SpendingCategory(
                name    = category,
                percent = (absAmount / totalSpend).toFloat().coerceIn(0f, 1f),
                amount  = formatCurrency(absAmount, symbol, displayCurrency),
                color   = colorMap[category] ?: categoryColors[index % categoryColors.size]
            )
        }
}

fun computeDashboardState(
    balances: List<BalanceData>,
    transactions: List<TransactionData>,
    accounts: List<AccountData>,
    displayCurrency: String,
    rates: Map<String, Double>
): DashboardState {
    val symbol       = getCurrencySymbol(displayCurrency)
    val now          = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val currentMonth = now.month.number
    val currentYear  = now.year

    // ── Balance — skip unconvertible accounts rather than misrepresenting them ─
    val currentBalance = balances.sumOf { balance ->
        convertBalance(balance.current, balance.currency, displayCurrency, rates) ?: 0.0
    }

    // ── Current month transactions ────────────────────────────────────────────
    val thisMonthTx = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        parts.size == 3 &&
                parts[0].toIntOrNull() == currentYear &&
                parts[1].toIntOrNull() == currentMonth
    }

    val monthlyIncome = thisMonthTx
        .filter { it.amount > 0 }
        .sumOf { tx ->
            val converted = tx.convertedAmount(displayCurrency, rates)
            if (converted != null) kotlin.math.abs(converted) else 0.0
        }

    val monthlyExpenses = thisMonthTx
        .filter { it.amount < 0 }
        .sumOf { tx ->
            val converted = tx.convertedAmount(displayCurrency, rates)
            if (converted != null) kotlin.math.abs(converted) else 0.0
        }

    // ── Last month for change % calculations ─────────────────────────────────
    val lastMonthDate   = now.date.minus(DatePeriod(months = 1))
    val lastMonthNumber = lastMonthDate.month.number
    val lastMonthYear   = lastMonthDate.year

    val lastMonthTx = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        parts.size == 3 &&
                parts[0].toIntOrNull() == lastMonthYear &&
                parts[1].toIntOrNull() == lastMonthNumber
    }

    val lastMonthIncome = lastMonthTx
        .filter { it.amount > 0 }
        .sumOf { tx ->
            val converted = tx.convertedAmount(displayCurrency, rates)
            if (converted != null) kotlin.math.abs(converted) else 0.0
        }

    val lastMonthExpenses = lastMonthTx
        .filter { it.amount < 0 }
        .sumOf { tx ->
            val converted = tx.convertedAmount(displayCurrency, rates)
            if (converted != null) kotlin.math.abs(converted) else 0.0
        }

    fun calculateChange(current: Double, previous: Double): Float {
        if (previous == 0.0) return 0f
        return (((current - previous) / previous) * 100).toFloat()
    }

    val incomeChangePercent  = calculateChange(monthlyIncome, lastMonthIncome)
    val expenseChangePercent = calculateChange(monthlyExpenses, lastMonthExpenses)

    val lastMonthNet = lastMonthIncome - lastMonthExpenses
    val thisMonthNet = monthlyIncome - monthlyExpenses
    val balanceChangePercent = if (lastMonthNet != 0.0) {
        (((thisMonthNet - lastMonthNet) / kotlin.math.abs(lastMonthNet)) * 100).toFloat()
    } else 0f

    // ── Spending categories (this month, debits only, converted) ─────────────
    val debitTx    = thisMonthTx.filter { it.amount < 0 }
    val totalSpend = debitTx.sumOf { tx ->
        val converted = tx.convertedAmount(displayCurrency, rates)
        if (converted != null) kotlin.math.abs(converted) else 0.0
    }.takeIf { it > 0 } ?: 1.0
    val colorMap   = categoryNames.zip(categoryColors).toMap()

    val spendingCategories = debitTx
        .groupBy { TransactionCategories.normalize(it.category) }
        .entries
        .sortedByDescending { entry ->
            entry.value.sumOf { tx ->
                val converted = tx.convertedAmount(displayCurrency, rates)
                if (converted != null) kotlin.math.abs(converted) else 0.0
            }
        }
        .mapIndexed { index, (category, txList) ->
            val absAmount = txList.sumOf { tx ->
                val converted = tx.convertedAmount(displayCurrency, rates)
                if (converted != null) kotlin.math.abs(converted) else 0.0
            }
            SpendingCategory(
                name    = category,
                percent = (absAmount / totalSpend).toFloat().coerceIn(0f, 1f),
                amount  = formatCurrency(absAmount, symbol, displayCurrency),
                color   = colorMap[category] ?: categoryColors[index % categoryColors.size]
            )
        }

    // ── Monthly trend (last 6 months, converted) ──────────────────────────────
    val monthLabels  = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val monthlyTrend = (5 downTo 0).map { monthsAgo ->
        val targetDate  = now.date.minus(DatePeriod(months = monthsAgo))
        val targetMonth = targetDate.month.number
        val targetYear  = targetDate.year
        val monthName   = monthLabels[targetMonth - 1]

        val monthTx = transactions.filter { tx ->
            val parts = tx.timestamp.take(10).split("-")
            parts.size == 3 &&
                    parts[0].toIntOrNull() == targetYear &&
                    parts[1].toIntOrNull() == targetMonth
        }

        MonthlyPoint(
            month    = monthName,
            income   = monthTx.filter { it.amount > 0 }
                .sumOf { tx ->
                    val converted = tx.convertedAmount(displayCurrency, rates)
                    if (converted != null) kotlin.math.abs(converted) else 0.0
                }.toFloat(),
            expenses = monthTx.filter { it.amount < 0 }
                .sumOf { tx ->
                    val converted = tx.convertedAmount(displayCurrency, rates)
                    if (converted != null) kotlin.math.abs(converted) else 0.0
                }.toFloat()
        )
    }

    // ── Recent transactions — shown in ORIGINAL currency (not converted) ──────
    val recentTransactions = transactions.take(10).map { tx ->
        val txSymbol  = getCurrencySymbol(tx.currency)
        val formatted = if (tx.type.uppercase() == "CREDIT")
            "+${formatCurrency(tx.amount, txSymbol, tx.currency)}"
        else
            formatCurrency(tx.amount, txSymbol, tx.currency)
        Transaction(
            name     = tx.merchantName?.ifBlank { null } ?: tx.description,
            date     = formatDate(tx.timestamp),
            amount   = formatted,
            isIncome = tx.type.uppercase() == "CREDIT"
        )
    }

    // ── Accounts overview — balance converted, shown as "--" if unconvertible ─
    val accountOverviews = accounts.map { account ->
        val balance = balances.find { it.accountId == account.accountId }
        val convertedBalance = if (balance != null) {
            convertBalance(balance.current, balance.currency, displayCurrency, rates)
        } else null

        AccountOverview(
            accountId    = account.accountId,
            bankName     = account.bankName,
            maskedNumber = account.maskedNumber,
            balance      = if (convertedBalance != null)
                formatCurrency(convertedBalance, symbol, displayCurrency) else "--"
        )
    }

    // ── Monthly top spending category (last 6 months, converted) ─────────────
    val monthlyTopCategories = (5 downTo 0).mapNotNull { monthsAgo ->
        val targetDate  = now.date.minus(DatePeriod(months = monthsAgo))
        val targetMonth = targetDate.month.number
        val targetYear  = targetDate.year
        val monthName   = monthLabels[targetMonth - 1]

        val monthDebits = transactions.filter { tx ->
            val parts = tx.timestamp.take(10).split("-")
            parts.size == 3 &&
                    parts[0].toIntOrNull() == targetYear &&
                    parts[1].toIntOrNull() == targetMonth &&
                    tx.amount < 0
        }
        if (monthDebits.isEmpty()) return@mapNotNull null

        val topCategory = monthDebits
            .groupBy { TransactionCategories.normalize(it.category) }
            .maxByOrNull { entry ->
                entry.value.sumOf { tx ->
                    val converted = tx.convertedAmount(displayCurrency, rates)
                    if (converted != null) kotlin.math.abs(converted) else 0.0
                }
            } ?: return@mapNotNull null

        MonthlyTopCategory(
            month    = monthName,
            category = topCategory.key,
            amount   = topCategory.value.sumOf { tx ->
                val converted = tx.convertedAmount(displayCurrency, rates)
                if (converted != null) kotlin.math.abs(converted) else 0.0
            }.toFloat(),
            color    = colorMap[topCategory.key] ?: categoryColors[0]
        )
    }

    return DashboardState(
        currentBalance        = currentBalance,
        monthlyIncome         = monthlyIncome,
        monthlyExpenses       = monthlyExpenses,
        currency              = displayCurrency,
        spendingCategories    = spendingCategories,
        monthlyTrend          = monthlyTrend,
        recentTransactions    = recentTransactions,
        accounts              = accountOverviews,
        monthlyTopCategories  = monthlyTopCategories,
        rawTransactions       = transactions,
        balanceChangePercent  = balanceChangePercent,
        incomeChangePercent   = incomeChangePercent,
        expenseChangePercent  = expenseChangePercent
    )
}