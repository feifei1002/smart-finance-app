package com.smart_finance_app.dashboard

import androidx.compose.ui.graphics.Color
import com.smart_finance_app.StringKey

data class SpendingCategory(val name: String, val percent: Float, val amount: String, val color: Color)
data class MonthlyPoint(val month: String, val income: Float, val expenses: Float)
data class Transaction(val name: String, val date: String, val amount: String, val isIncome: Boolean)
data class AccountOverview(val accountId: String, val bankName: String, val maskedNumber: String, val balance: String, val balanceValue: Double?)


// ── Chart card catalogue ──────────────────────────────────────────────────────

enum class CardSize { FULL, HALF }

data class ChartCardDef(
    val key: String,
    val title: StringKey,
    val description: StringKey,
    val size: CardSize,
    val builtIn: Boolean = false,
    val restorable: Boolean = false
)

private val DASHBOARD_CARD_DEFINITIONS = listOf(
    ChartCardDef(
        key = "spending",
        title = StringKey.DASHBOARD_SPENDING_PERIOD,
        description = StringKey.DASHBOARD_SPENDING_PERIOD_DESC,
        size = CardSize.FULL,
        builtIn = true,
        restorable = true
    ),
    ChartCardDef(
        key = "trend",
        title = StringKey.DASHBOARD_MONTHLY_TREND,
        description = StringKey.CHART_WEEKLY_SPENDING_DESC,
        size = CardSize.HALF,
        builtIn = true,
        restorable = false
    ),
    ChartCardDef(
        key = "top_categories",
        title = StringKey.DASHBOARD_HIGHEST_SPENDING,
        description = StringKey.CHART_MERCHANT_FREQUENCY_DESC,
        size = CardSize.HALF,
        builtIn = true,
        restorable = true
    ),

    /** All chart cards available to add via the + Charts sheet. */
    ChartCardDef("weekly_spending", StringKey.CHART_WEEKLY_SPENDING_TITLE, StringKey.CHART_WEEKLY_SPENDING_DESC, CardSize.FULL),
    ChartCardDef("bank_comparison", StringKey.CHART_BANK_COMPARISON_TITLE, StringKey.CHART_BANK_COMPARISON_DESC, CardSize.HALF),
    ChartCardDef("time_of_day", StringKey.CHART_TIME_OF_DAY_TITLE, StringKey.CHART_TIME_OF_DAY_DESC, CardSize.HALF),
    ChartCardDef("largest_tx", StringKey.CHART_LARGEST_TX_TITLE, StringKey.CHART_LARGEST_TX_DESC, CardSize.HALF),
    ChartCardDef("smallest_tx", StringKey.CHART_SMALLEST_TX_TITLE, StringKey.CHART_SMALLEST_TX_DESC, CardSize.HALF),
    ChartCardDef("merchant_frequency", StringKey.CHART_MERCHANT_FREQUENCY_TITLE, StringKey.CHART_MERCHANT_FREQUENCY_DESC, CardSize.FULL)
)
val ALL_CHART_CARDS = DASHBOARD_CARD_DEFINITIONS.filter { !it.builtIn }

val RESTORABLE_BUILT_IN_CARDS =
    DASHBOARD_CARD_DEFINITIONS.filter { it.builtIn && it.restorable }

internal val BUILT_IN_DASHBOARD_CARD_KEYS =
    DASHBOARD_CARD_DEFINITIONS.filter { it.builtIn }.map { it.key }.toSet()

internal fun dashboardCardDef(key: String): ChartCardDef? =
    DASHBOARD_CARD_DEFINITIONS.firstOrNull { it.key == key }

internal fun isHalfCardKey(key: String): Boolean =
    dashboardCardDef(key)?.size == CardSize.HALF

internal fun isActiveChartCardKey(key: String): Boolean =
    dashboardCardDef(key)?.builtIn == false

internal fun isKnownDashboardCardKey(key: String): Boolean =
    dashboardCardDef(key) != null