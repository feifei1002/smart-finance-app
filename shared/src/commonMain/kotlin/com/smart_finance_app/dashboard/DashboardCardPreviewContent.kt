package com.smart_finance_app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.getCurrencySymbol
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
internal fun DashboardCardPreviewContent(
    cardKey: String,
    state: DashboardState,
    rawTransactions: List<TransactionData>,
    spendingPeriod: SpendingPeriod,
    displayCurrency: String,
    rates: Map<String, Double>
) {
    when (cardKey) {
        "spending" -> {
            val filteredCategories = computeSpendingCategories(
                transactions = rawTransactions,
                period = spendingPeriod,
                displayCurrency = displayCurrency,
                rates = rates
            )

            val chartCategories = filteredCategories.filter { it.percent >= 0.01f }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DonutChart(
                    categories = chartCategories,
                    modifier = Modifier.size(220.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chartCategories.forEach { cat ->
                        CategoryLegendRow(cat)
                    }
                }
            }
        }

        "time_of_day" -> {
            val sym = getCurrencySymbol(displayCurrency)
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            val morningLabel = appStringResource(StringKey.TIME_OF_DAY_MORNING)
            val afternoonLabel = appStringResource(StringKey.TIME_OF_DAY_AFTERNOON)
            val nightLabel = appStringResource(StringKey.TIME_OF_DAY_NIGHT)

            val buckets = mapOf(
                "Morning" to Pair(morningLabel, Color(0xFFF59E0B)),
                "Afternoon" to Pair(afternoonLabel, Color(0xFF6366F1)),
                "Night" to Pair(nightLabel, Color(0xFF1E40AF))
            )

            val grouped = rawTransactions
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .groupBy { tx ->
                    val hour = tx.timestamp.drop(11).take(2).toIntOrNull() ?: 12
                    when {
                        hour < 12 -> "Morning"
                        hour < 18 -> "Afternoon"
                        else -> "Night"
                    }
                }

            val total = grouped.values
                .flatten()
                .sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }
                .takeIf { it > 0 }
                ?: 1.0

            val cats = buckets.map { (key, pair) ->
                val (label, color) = pair
                val amount = grouped[key]
                    ?.sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }
                    ?: 0.0

                SpendingCategory(
                    name = label,
                    percent = (amount / total).toFloat(),
                    amount = formatCurrency(amount, sym, displayCurrency),
                    color = color
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DonutChart(
                    categories = cats,
                    modifier = Modifier.size(220.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cats.forEach { cat ->
                        CategoryLegendRow(cat)
                    }
                }
            }
        }

        "trend",
        "top_categories" -> {
            HalfCardContent(
                cardKey = cardKey,
                state = state,
                showTitle = false
            )
        }

        else -> {
            ChartCardContent(
                key = cardKey,
                state = state,
                rawTransactions = rawTransactions,
                displayCurrency = displayCurrency,
                rates = rates,
                showTitle = false
            )
        }
    }
}