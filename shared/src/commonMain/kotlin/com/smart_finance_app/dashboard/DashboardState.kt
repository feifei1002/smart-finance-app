package com.smart_finance_app.dashboard

import androidx.compose.ui.graphics.Color
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Holds all computed dashboard data derived from API responses.
 * Built by [computeDashboardState] from raw balance + transaction + account data.
 */
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

// ── Category colours ──────────────────────────────────────────────────────────

private val categoryColors = listOf(
    Color(0xFF6366F1),
    Color(0xFF22C55E),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF3B82F6),
    Color(0xFF94A3B8),
)

private val categoryNames = listOf(
    "Housing", "Food", "Transport", "Shopping", "Entertainment", "Other"
)

// Keyword-based categorisation — replace with ML/backend logic later
private fun categorise(description: String, merchantName: String?): String {
    val text = (merchantName ?: description).lowercase()
    return when {
        text.contains("rent") || text.contains("mortgage") || text.contains("utilities")
                || text.contains("electricity") || text.contains("gas") || text.contains("water") -> "Housing"
        text.contains("tesco") || text.contains("sainsbury") || text.contains("waitrose")
                || text.contains("asda") || text.contains("aldi") || text.contains("lidl")
                || text.contains("grocery") || text.contains("food") || text.contains("restaurant")
                || text.contains("cafe") || text.contains("coffee") || text.contains("starbucks")
                || text.contains("mcdonald") || text.contains("deliveroo") || text.contains("uber eats") -> "Food"
        text.contains("uber") || text.contains("lyft") || text.contains("taxi")
                || text.contains("tfl") || text.contains("train") || text.contains("bus")
                || text.contains("fuel") || text.contains("petrol") || text.contains("parking") -> "Transport"
        text.contains("amazon") || text.contains("asos") || text.contains("ebay")
                || text.contains("zara") || text.contains("h&m") || text.contains("primark")
                || text.contains("shopping") || text.contains("store") -> "Shopping"
        text.contains("netflix") || text.contains("spotify") || text.contains("cinema")
                || text.contains("disney") || text.contains("apple") || text.contains("game")
                || text.contains("entertainment") -> "Entertainment"
        else -> "Other"
    }
}

fun getCurrencySymbol(currency: String) = when (currency.uppercase()) {
    "GBP" -> "£"
    "EUR" -> "€"
    "USD" -> "$"
    "TWD" -> "NT$"
    else  -> currency
}

/** KMP-compatible currency formatter — avoids String.format() which is JVM-only */
fun formatCurrency(value: Double, symbol: String): String {
    val absValue = kotlin.math.abs(value)
    val intPart  = absValue.toLong()
    val decPart  = kotlin.math.round((absValue - intPart) * 100).toLong()
    val prefix   = if (value < 0) "-" else ""
    return "$prefix$symbol$intPart.${decPart.toString().padStart(2, '0')}"
}

fun formatDate(timestamp: String): String {
    return try {
        val date = timestamp.take(10).split("-")
        val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        "${months[date[1].toInt()]} ${date[2].toInt()}, ${date[0]}"
    } catch (e: Exception) {
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
 * Computes spending categories for a given period.
 * Called when user changes the calendar filter on the spending overview.
 */
fun computeSpendingCategories(
    transactions: List<TransactionData>,
    period: SpendingPeriod,
    currency: String
): List<SpendingCategory> {
    val symbol = getCurrencySymbol(currency)
    val now    = Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())

    val filtered = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        if (parts.size != 3) return@filter false
        val txYear  = parts[0].toIntOrNull() ?: return@filter false
        val txMonth = parts[1].toIntOrNull() ?: return@filter false
        val txDay   = parts[2].toIntOrNull() ?: return@filter false
        when (period) {
            SpendingPeriod.THIS_MONTH -> txYear == now.year && txMonth == now.monthNumber
            SpendingPeriod.LAST_MONTH -> {
                val last = now.date.minus(kotlinx.datetime.DatePeriod(months = 1))
                txYear == last.year && txMonth == last.monthNumber
            }
            SpendingPeriod.LAST_3_MONTHS -> {
                val cutoff = now.date.minus(kotlinx.datetime.DatePeriod(months = 3))
                (txYear > cutoff.year) || (txYear == cutoff.year && txMonth >= cutoff.monthNumber)
            }
            SpendingPeriod.THIS_YEAR -> txYear == now.year
        }
    }.filter { it.amount < 0 }

    val totalSpend = filtered.sumOf { kotlin.math.abs(it.amount) }.takeIf { it > 0 } ?: 1.0
    val colorMap   = categoryNames.zip(categoryColors).toMap()

    return filtered
        .groupBy { categorise(it.description, it.merchantName) }
        .entries
        .sortedByDescending { it.value.sumOf { tx -> kotlin.math.abs(tx.amount) } }
        .mapIndexed { index, (category, txList) ->
            val absAmount = txList.sumOf { kotlin.math.abs(it.amount) }
            SpendingCategory(
                name    = category,
                percent = (absAmount / totalSpend).toFloat().coerceIn(0f, 1f),
                amount  = formatCurrency(absAmount, symbol),
                color   = colorMap[category] ?: categoryColors[index % categoryColors.size]
            )
        }
}

/**
 * Converts raw API data into a [DashboardState] ready for the UI.
 */
fun computeDashboardState(
    balances: List<BalanceData>,
    transactions: List<TransactionData>,
    accounts: List<AccountData>
): DashboardState {
    val currency = balances.firstOrNull()?.currency ?: "GBP"
    val symbol   = getCurrencySymbol(currency)

    // ── Balance ───────────────────────────────────────────────────────────────
    val currentBalance = balances.sumOf { it.current }

    // ── Current month income + expenses ───────────────────────────────────────
    val now          = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val currentMonth = now.monthNumber
    val currentYear  = now.year

    val thisMonthTx = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        parts.size == 3 &&
                parts[0].toIntOrNull() == currentYear &&
                parts[1].toIntOrNull() == currentMonth
    }

    val monthlyIncome   = thisMonthTx.filter { it.amount > 0 }.sumOf { it.amount }
    val monthlyExpenses = thisMonthTx.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }

    // ── Last month income + expenses calculation ──────────────────────────────
    val lastMonthDate  = now.date.minus(DatePeriod(months = 1))
    val lastMonthNumber = lastMonthDate.monthNumber
    val lastMonthYear   = lastMonthDate.year

    val lastMonthTx = transactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        parts.size == 3 &&
                parts[0].toIntOrNull() == lastMonthYear &&
                parts[1].toIntOrNull() == lastMonthNumber
    }

    val lastMonthIncome   = lastMonthTx.filter { it.amount > 0 }.sumOf { it.amount }
    val lastMonthExpenses = lastMonthTx.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }

    // Helper math to calculate the shift safely (handles division by zero)
    fun calculateChange(current: Double, previous: Double): Float {
        if (previous == 0.0) return 0f
        return (((current - previous) / previous) * 100).toFloat()
    }

    val incomeChangePercent  = calculateChange(monthlyIncome, lastMonthIncome)
    val expenseChangePercent = calculateChange(monthlyExpenses, lastMonthExpenses)

    // Balance change tracks net savings progression contextually
    val lastMonthNet = lastMonthIncome - lastMonthExpenses
    val thisMonthNet = monthlyIncome - monthlyExpenses
    val balanceChangePercent = if (lastMonthNet != 0.0) {
        (((thisMonthNet - lastMonthNet) / kotlin.math.abs(lastMonthNet)) * 100).toFloat()
    } else 0f

    // ── Spending categories (this month, debits only) ─────────────────────────
    val debitTx    = thisMonthTx.filter { it.amount < 0 }
    val totalSpend = debitTx.sumOf { kotlin.math.abs(it.amount) }.takeIf { it > 0 } ?: 1.0
    val colorMap   = categoryNames.zip(categoryColors).toMap()

    val spendingCategories = debitTx
        .groupBy { categorise(it.description, it.merchantName) }
        .entries
        .sortedByDescending { it.value.sumOf { tx -> kotlin.math.abs(tx.amount) } }
        .mapIndexed { index, (category, txList) ->
            val absAmount = txList.sumOf { kotlin.math.abs(it.amount) }
            SpendingCategory(
                name    = category,
                percent = (absAmount / totalSpend).toFloat().coerceIn(0f, 1f),
                amount  = formatCurrency(absAmount, symbol),
                color   = colorMap[category] ?: categoryColors[index % categoryColors.size]
            )
        }

    // ── Monthly trend (last 6 months) ─────────────────────────────────────────
    val monthLabels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val monthlyTrend = (5 downTo 0).map { monthsAgo ->
        val targetDate  = now.date.minus(DatePeriod(months = monthsAgo))
        val targetMonth = targetDate.monthNumber
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
            income   = monthTx.filter { it.amount > 0 }.sumOf { it.amount }.toFloat(),
            expenses = monthTx.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }.toFloat()
        )
    }

    // ── Recent transactions (latest 10) ───────────────────────────────────────
    val recentTransactions = transactions.take(10).map { tx ->
        val formatted = if (tx.type.uppercase() == "CREDIT")
            "+${formatCurrency(tx.amount, symbol)}"
        else
            formatCurrency(tx.amount, symbol)
        Transaction(
            name     = tx.merchantName?.ifBlank { null } ?: tx.description,
            date     = formatDate(tx.timestamp),
            amount   = formatted,
            isIncome = tx.type.uppercase() == "CREDIT"
        )
    }

    // ── Accounts overview ─────────────────────────────────────────────────────
    val accountOverviews = accounts.mapNotNull { account ->
        val balance = balances.find { it.accountId == account.accountId }
        AccountOverview(
            bankName     = account.bankName,
            maskedNumber = account.maskedNumber,
            balance      = if (balance != null) formatCurrency(balance.current, symbol) else "--"
        )
    }

    // ── Monthly top spending category (last 6 months) ────────────────────────────
    val monthLabels2 = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val monthlyTopCategories = (5 downTo 0).mapNotNull { monthsAgo ->
        val targetDate  = now.date.minus(DatePeriod(months = monthsAgo))
        val targetMonth = targetDate.monthNumber
        val targetYear  = targetDate.year
        val monthName   = monthLabels2[targetMonth - 1]

        val monthDebits = transactions.filter { tx ->
            val parts = tx.timestamp.take(10).split("-")
            parts.size == 3 &&
                    parts[0].toIntOrNull() == targetYear &&
                    parts[1].toIntOrNull() == targetMonth &&
                    tx.amount < 0
        }
        if (monthDebits.isEmpty()) return@mapNotNull null

        val topCategory = monthDebits
            .groupBy { categorise(it.description, it.merchantName) }
            .maxByOrNull { it.value.sumOf { tx -> kotlin.math.abs(tx.amount) } }
            ?: return@mapNotNull null

        MonthlyTopCategory(
            month    = monthName,
            category = topCategory.key,
            amount   = topCategory.value.sumOf { kotlin.math.abs(it.amount) }.toFloat(),
            color    = colorMap[topCategory.key] ?: categoryColors[0]
        )
    }

    return DashboardState(
        currentBalance        = currentBalance,
        monthlyIncome         = monthlyIncome,
        monthlyExpenses       = monthlyExpenses,
        currency              = currency,
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

/**
 * Filters and recomputes spending categories based on the selected period.
 * Called from DashboardScreen when the user changes the calendar period.
 */
fun filterCategoriesByPeriod(
    state: DashboardState,
    period: com.smart_finance_app.dashboard.SpendingPeriod
): List<SpendingCategory> {
    val now          = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val currentMonth = now.monthNumber
    val currentYear  = now.year
    val symbol       = getCurrencySymbol(state.currency)

    val filtered = state.rawTransactions.filter { tx ->
        val parts = tx.timestamp.take(10).split("-")
        if (parts.size != 3) return@filter false
        val txYear  = parts[0].toIntOrNull() ?: return@filter false
        val txMonth = parts[1].toIntOrNull() ?: return@filter false
        val txDay   = parts[2].toIntOrNull() ?: return@filter false
        if (tx.amount >= 0) return@filter false  // only expenses

        when (period) {
            com.smart_finance_app.dashboard.SpendingPeriod.THIS_MONTH ->
                txYear == currentYear && txMonth == currentMonth
            com.smart_finance_app.dashboard.SpendingPeriod.LAST_MONTH -> {
                val lastMonth = if (currentMonth == 1) 12 else currentMonth - 1
                val lastYear  = if (currentMonth == 1) currentYear - 1 else currentYear
                txYear == lastYear && txMonth == lastMonth
            }
            com.smart_finance_app.dashboard.SpendingPeriod.LAST_3_MONTHS -> {
                val cutoff = now.date.minus(DatePeriod(months = 3))
                val txDate = kotlinx.datetime.LocalDate(txYear, txMonth, txDay)
                txDate >= cutoff
            }
            com.smart_finance_app.dashboard.SpendingPeriod.THIS_YEAR ->
                txYear == currentYear
        }
    }

    if (filtered.isEmpty()) return emptyList()

    val totalSpend = filtered.sumOf { kotlin.math.abs(it.amount) }.takeIf { it > 0 } ?: 1.0
    val colorMap   = listOf("Housing","Food","Transport","Shopping","Entertainment","Other")
        .zip(listOf(
            androidx.compose.ui.graphics.Color(0xFF6366F1),
            androidx.compose.ui.graphics.Color(0xFF22C55E),
            androidx.compose.ui.graphics.Color(0xFFF59E0B),
            androidx.compose.ui.graphics.Color(0xFFEC4899),
            androidx.compose.ui.graphics.Color(0xFF3B82F6),
            androidx.compose.ui.graphics.Color(0xFF94A3B8)
        )).toMap()

    return filtered
        .groupBy { categorise(it.description, it.merchantName) }
        .entries
        .sortedByDescending { it.value.sumOf { tx -> kotlin.math.abs(tx.amount) } }
        .mapIndexed { i, (category, txList) ->
            val absAmount = txList.sumOf { kotlin.math.abs(it.amount) }
            SpendingCategory(
                name    = category,
                percent = (absAmount / totalSpend).toFloat().coerceIn(0f, 1f),
                amount  = formatCurrency(absAmount, symbol),
                color   = colorMap[category] ?: categoryColors[i % categoryColors.size]
            )
        }
}