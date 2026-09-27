package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.ConversionResult
import com.smart_finance_app.currency.ExchangeRateService
import com.smart_finance_app.currency.getCurrencySymbol
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Clock

// ── + Charts card content dispatcher ─────────────────────────────────────────

@Composable
internal fun ChartCardContent(
    key: String,
    state: DashboardState,
    rawTransactions: List<TransactionData>,
    displayCurrency: String,
    rates: Map<String, Double>
) {
    val sym = getCurrencySymbol(displayCurrency)
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    when (key) {
        // ── Weekly Spending (full) ──
        "weekly_spending" -> {
            val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
            val dayOfWeek = now.dayOfWeek.ordinal
            val weeklyData = (6 downTo 0).map { daysAgo ->
                val dayIdx = ((dayOfWeek - daysAgo + 70) % 7)
                val dayLabel = days[dayIdx]
                val targetDate = now.date.minus(DatePeriod(days = daysAgo))
                val total = rawTransactions
                    .filter { tx ->
                        val p = tx.timestamp.take(10).split("-")
                        p.size == 3 &&
                                p[0].toIntOrNull() == targetDate.year &&
                                p[1].toIntOrNull() == targetDate.month.number &&
                                p[2].toIntOrNull() == targetDate.dayOfMonth &&
                                tx.amount < 0
                    }
                    .sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }.toFloat()
                MonthlyTopCategory(month = dayLabel, category = "", amount = total, color = Color(0xFF6366F1))
            }
            // 1. Center the chart content vertically and horizontally inside the card
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        appStringResource(StringKey.CHART_WEEKLY_SPENDING_TITLE),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    BarChart(data = weeklyData, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
            }
        }

        // ── Bank Account Comparison (half) ──
        // 2. Scrollable inside the card so no info is cut
        "bank_comparison" -> {
            val accountSpend = state.accounts.map { acc ->
                val total = rawTransactions
                    .filter { tx: TransactionData ->
                        val p = tx.timestamp.take(10).split("-")
                        p.size == 3 &&
                                p[0].toIntOrNull() == now.year &&
                                p[1].toIntOrNull() == now.month.number &&
                                tx.amount < 0 &&
                                tx.accountId == acc.accountId
                    }
                    .sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }
                    .toFloat()

                acc.bankName to total
            }
            val maxAmt = accountSpend.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1f
            val barColors = listOf(Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEC4899))
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    appStringResource(StringKey.CHART_BANK_COMPARISON_TITLE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accountSpend.forEachIndexed { i, (name, amount) ->
                        val fraction = (amount / maxAmt).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).height(8.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                                        .background(barColors[i % barColors.size], RoundedCornerShape(4.dp)))
                                }
                                Text(
                                    text = formatCurrency(amount.toDouble(), sym, displayCurrency),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Spending by Time of Day (half) ──
        // 3. Everything centered inside the card
        "time_of_day" -> {
            fun hourFromTimestamp(timestamp: String): Int? =
                timestamp.drop(11).take(2).toIntOrNull()?.takeIf { it in 0..23 }

            val buckets = listOf(
                Triple("Morning", appStringResource(StringKey.TIME_OF_DAY_MORNING), Color(0xFFF59E0B)),
                Triple("Afternoon", appStringResource(StringKey.TIME_OF_DAY_AFTERNOON), Color(0xFF6366F1)),
                Triple("Night", appStringResource(StringKey.TIME_OF_DAY_NIGHT), Color(0xFF1E40AF))
            )

            val grouped = rawTransactions
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0 &&
                            hourFromTimestamp(tx.timestamp) != null
                }
                .groupBy { tx ->
                    val hour = hourFromTimestamp(tx.timestamp) ?: 0
                    when {
                        hour < 12 -> "Morning"
                        hour < 18 -> "Afternoon"
                        else -> "Night"
                    }
                }

            val total = grouped.values.flatten()
                .sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }

            val cats = buckets.map { (key, label, color) ->
                val amount = grouped[key]?.sumOf {
                    convertedAbsAmount(it.amount, it.currency, displayCurrency, rates)
                } ?: 0.0

                SpendingCategory(
                    name = label,
                    percent = if (total > 0.0) (amount / total).toFloat() else 0f,
                    amount = formatCurrency(amount, sym, displayCurrency),
                    color = color
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    appStringResource(StringKey.CHART_TIME_OF_DAY_TITLE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (total <= 0.0) {
                    Text(appStringResource(StringKey.CHART_NO_DATA), style = MaterialTheme.typography.bodySmall)
                } else {
                    DonutChart(categories = cats, modifier = Modifier.size(64.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        cats.forEach { cat ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).background(cat.color, CircleShape))
                                Text(
                                    "${cat.name} ${(cat.percent * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Largest Transactions (half) ──
        // 4. Group by description, sum duplicates, then rank top 5
        "largest_tx" -> {
            val top5 = rawTransactions
                .asSequence()
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .groupBy { tx -> tx.merchantName?.ifBlank { null } ?: tx.description }
                .map { (name, txList) -> name to txList.sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) } }
                .sortedByDescending { it.second }
                .take(5)
                .toList()
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    appStringResource(StringKey.CHART_LARGEST_TX_TITLE), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (top5.isEmpty()) {
                    Text(appStringResource(StringKey.CHART_NO_DATA), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                        top5.forEachIndexed { i, (name, amount) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(14.dp)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatCurrency(amount, getCurrencySymbol(displayCurrency), displayCurrency),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Smallest Transactions (half) ──
        // 4. Same grouping logic — sum duplicates, then rank bottom 5
        "smallest_tx" -> {
            val bottom5 = rawTransactions
                .asSequence()
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .groupBy { tx -> tx.merchantName?.ifBlank { null } ?: tx.description }
                .map { (name, txList) -> name to txList.sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) } }
                .sortedBy { it.second }
                .take(5)
                .toList()
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    appStringResource(StringKey.CHART_SMALLEST_TX_TITLE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (bottom5.isEmpty()) {
                    Text(appStringResource(StringKey.CHART_NO_DATA), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                        bottom5.forEachIndexed { i, (name, amount) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(14.dp)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatCurrency(amount, getCurrencySymbol(displayCurrency), displayCurrency),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Merchant Spending Treemap (full) ──
        "merchant_frequency" -> {
            data class MerchantBubble(
                val name: String,
                val visitCount: Int,
                val avgAmount: Double,
                val totalSpend: Double,
                val color: Color
            )
            val bubbleColors = listOf(
                Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF59E0B),
                Color(0xFFEC4899), Color(0xFF3B82F6)
            )
            val thisMonthTx = rawTransactions.filter { tx ->
                val p = tx.timestamp.take(10).split("-")
                p.size == 3 &&
                        p[0].toIntOrNull() == now.year &&
                        p[1].toIntOrNull() == now.month.number &&
                        tx.amount < 0
            }
            val bubbles = thisMonthTx
                .groupBy { tx -> tx.merchantName?.ifBlank { null } ?: tx.description }
                .map { (name, txList) ->
                    val count = txList.size
                    val total = txList.sumOf { convertedAbsAmount(it.amount, it.currency, displayCurrency, rates) }
                    name to Triple(count, total / count, total)
                }
                .sortedByDescending { it.second.third }
                .mapIndexed { i, (name, triple) ->
                    MerchantBubble(name, triple.first, triple.second, triple.third, bubbleColors[i % bubbleColors.size])
                }
                .take(10)

            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        appStringResource(StringKey.CHART_MERCHANT_FREQUENCY_TITLE),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(appStringResource(StringKey.CHART_AREA_TOTAL_SPEND),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (bubbles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(appStringResource(StringKey.CHART_NO_DATA_THIS_MONTH), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Horizontally scrollable treemap — each merchant gets a tile
                    // whose width is proportional to total spend.  The user can
                    // swipe/scroll to see smaller merchants on the right.
                    val totalTreemapSpend = bubbles.sumOf { it.totalSpend }.toFloat().coerceAtLeast(1f)
                    val textMeasurer = rememberTextMeasurer()
                    val tilePadding  = 2.dp
                    // Minimum tile width so text is always legible
                    val minTileWidthDp = 64.dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        bubbles.forEach { b ->
                            // Natural proportional width — floor at minTileWidthDp
                            val fraction  = (b.totalSpend.toFloat() / totalTreemapSpend)
                            val naturalDp = (fraction * 320).dp   // 320dp ref — fits ~2 tiles before scroll
                            val tileDp    = maxOf(naturalDp, minTileWidthDp)

                            Canvas(
                                modifier = Modifier
                                    .width(tileDp)
                                    .fillMaxHeight()
                                    .padding(tilePadding)
                            ) {
                                val w = size.width
                                val h = size.height
                                drawRoundRect(
                                    color = b.color.copy(alpha = 0.84f),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(w, h),
                                    cornerRadius = CornerRadius(8.dp.toPx())
                                )
                                val r = minOf(w, h) / 2f
                                val cx = w / 2f
                                val cy = h / 2f

                                val nameStyle = TextStyle(
                                    fontSize = (r * 0.30f / density).coerceAtLeast(8f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                val amountStyle = TextStyle(
                                    fontSize = (r * 0.23f / density).coerceAtLeast(7f).sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    textAlign = TextAlign.Center
                                )

                                val nameLayout = textMeasurer.measure(
                                    b.name.take(14), nameStyle,
                                    constraints = Constraints(maxWidth = w.toInt().coerceAtLeast(1))
                                )
                                val amountLayout = textMeasurer.measure(
                                    formatCurrency(b.totalSpend, sym, displayCurrency), amountStyle
                                )

                                val totalTextH = nameLayout.size.height + amountLayout.size.height + 2.dp.toPx()
                                drawText(nameLayout,   topLeft = Offset(cx - nameLayout.size.width   / 2f, cy - totalTextH / 2f))
                                drawText(amountLayout, topLeft = Offset(cx - amountLayout.size.width / 2f,
                                    cy - totalTextH / 2f + nameLayout.size.height + 2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun convertedAbsAmount(
    amount: Double,
    fromCurrency: String,
    displayCurrency: String,
    rates: Map<String, Double>
): Double {
    return when (val result = ExchangeRateService.convert(
        amount = amount,
        fromCurrency = fromCurrency,
        toCurrency = displayCurrency,
        rates = rates
    )) {
        is ConversionResult.Success -> abs(result.amount)
        else -> 0.0
    }
}
