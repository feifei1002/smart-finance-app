package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

// ── Hardcoded data models ────────────────────────────────────────────────────
// TODO: replace with real API data when backend is ready

data class SpendingCategory(val name: String, val percent: Float, val amount: String, val color: Color)
data class BudgetItem(val category: String, val spent: Float, val total: Float, val color: Color)
data class MonthlyPoint(val month: String, val income: Float, val expenses: Float)
data class Transaction(val name: String, val date: String, val amount: String, val isIncome: Boolean)
data class AccountOverview(val bankName: String, val maskedNumber: String, val balance: String)

val hardcodedCategories = listOf(
    SpendingCategory("Housing",   0.40f, "£1,593.68", Color(0xFF6366F1)),
    SpendingCategory("Food",      0.20f, "£796.84",   Color(0xFF22C55E)),
    SpendingCategory("Transport", 0.15f, "£597.63",   Color(0xFFF59E0B)),
    SpendingCategory("Shopping",  0.10f, "£398.42",   Color(0xFFEC4899)),
    SpendingCategory("Other",     0.15f, "£597.63",   Color(0xFF94A3B8)),
)

val hardcodedBudgets = listOf(
    BudgetItem("Food",      340f, 400f, Color(0xFF22C55E)),
    BudgetItem("Transport", 280f, 300f, Color(0xFFF59E0B)),
    BudgetItem("Shopping",  180f, 200f, Color(0xFFEC4899)),
)

val hardcodedMonthlyTrend = listOf(
    MonthlyPoint("Jan", 4800f, 3200f),
    MonthlyPoint("Feb", 5100f, 3600f),
    MonthlyPoint("Mar", 4900f, 4100f),
    MonthlyPoint("Apr", 5300f, 3800f),
    MonthlyPoint("May", 5000f, 3500f),
    MonthlyPoint("Jun", 5200f, 3984f),
)

val hardcodedTransactions = listOf(
    Transaction("Starbucks",      "May 15, 2024", "-£5.45",     false),
    Transaction("Amazon",         "May 14, 2024", "-£42.99",    false),
    Transaction("Salary Deposit", "May 13, 2024", "+£2,600.00", true),
    Transaction("Grocery Store",  "May 13, 2024", "-£64.21",    false),
    Transaction("Netflix",        "May 13, 2024", "-£15.49",    false),
)

val hardcodedAccounts = listOf(
    AccountOverview("Chase Checking",  "1234", "£4,250.00"),
    AccountOverview("Bank of America", "5678", "£1,840.75"),
)

// ── Main Dashboard Screen ────────────────────────────────────────────────────

@Composable
fun DashboardScreen() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        if (compact) {
            MobileDashboard()
        } else {
            DesktopDashboard()
        }
    }
}

// ── Mobile Layout ────────────────────────────────────────────────────────────

@Composable
private fun MobileDashboard() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp)
    ) {
        // 1. Header with time-based greeting + last sync + refresh
        item {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val hour = now.hour
            val greeting = when {
                hour < 12 -> "Good morning"
                hour < 18 -> "Good afternoon"
                else      -> "Good night"
            }
            val emoji = when {
                hour < 12 -> "☀️"
                hour < 18 -> "👋"
                else      -> "🌙"
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$greeting, Alex $emoji",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Here's your financial overview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Current Balance
        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Current Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "£6,090.75",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "↑", color = Color(0xFF16A34A), fontSize = 14.sp)
                        Text(
                            text = "2.5% vs last month",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }
        }

        // 3. Income + Expenses
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Monthly Income", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("£5,200.00", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Monthly Expenses", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("£3,984.20", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }



        // 5. Spending Overview — donut chart
        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Spending Overview (This Month)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonutChart(
                            categories = hardcodedCategories,
                            modifier = Modifier.size(120.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            hardcodedCategories.forEach { cat ->
                                CategoryLegendRow(cat)
                            }
                        }
                    }
                }
            }
        }

        // 6. Monthly Trend + Monthly Spending side by side — same height row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionTitle("Monthly Trend")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LegendDot(color = Color(0xFF16A34A), label = "In")
                            LegendDot(color = Color(0xFFEF4444), label = "Ex")
                        }
                        LineChart(
                            data = hardcodedMonthlyTrend,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionTitle("Spending")
                        BarChart(
                            data = hardcodedMonthlyTrend,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                }
            }
        }

        // 7. Budget Progress with Add option
        item {
            BudgetProgressCard()
        }

        // 9. Recent Transactions
        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle("Recent Transactions")
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    hardcodedTransactions.forEach { tx ->
                        TransactionRow(tx)
                    }
                }
            }
        }
    }
}

// ── Desktop Layout ───────────────────────────────────────────────────────────

@Composable
private fun DesktopDashboard() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp)
    ) {
        // Header row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
            }
        }

        // Stats row — equal height, content top-aligned
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard("Current Balance",  "£6,090.75",  "↑ 2.8% vs last month", true,  Modifier.weight(1f).fillMaxHeight())
                StatCard("Monthly Income",   "£5,200.00",  "",                      true,  Modifier.weight(1f).fillMaxHeight())
                StatCard("Monthly Expenses", "£3,984.20",  "",                      false, Modifier.weight(1f).fillMaxHeight())
                StatCard("Net Savings",      "£1,215.80",  "↑ 18% vs last month",  true,  Modifier.weight(1f).fillMaxHeight())
            }
        }

        // Charts row — donut + line chart
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Spending overview
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Spending Overview (This Month)")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DonutChart(
                                categories = hardcodedCategories,
                                modifier = Modifier.size(140.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                hardcodedCategories.forEach { cat ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(10.dp)
                                                .background(cat.color, CircleShape)
                                        )
                                        Text("${cat.name}  ${(cat.percent * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text(cat.amount,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Monthly trend line chart
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Monthly Trend")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendDot(color = Color(0xFF16A34A), label = "Income")
                            LegendDot(color = Color(0xFFEF4444), label = "Expenses")
                        }
                        LineChart(
                            data = hardcodedMonthlyTrend,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                }
            }
        }

        // Second charts row — bar + budget — equal height
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionTitle("Monthly Spending Comparison")
                        BarChart(
                            data = hardcodedMonthlyTrend,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionTitle("Budget Progress")
                        hardcodedBudgets.forEach { budget ->
                            BudgetProgressRow(budget)
                        }
                    }
                }
            }
        }

        // Bottom row — transactions + accounts + quick actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recent transactions
                DashboardCard(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle("Recent Transactions")
                            Text("View all", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        hardcodedTransactions.forEach { tx -> TransactionRow(tx) }
                    }
                }

                // Accounts overview
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Accounts Overview")
                        hardcodedAccounts.forEach { account ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(account.bankName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text("**** ${account.maskedNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(account.balance,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("Connected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF16A34A))
                                }
                            }
                            if (account != hardcodedAccounts.last()) HorizontalDivider()
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Account", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Quick actions
                DashboardCard(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Quick Actions")
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Connect Account", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Text("Create Budget", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        SectionTitle("Upcoming Bills")
                        UpcomingBillRow("Electricity Bill", "May 30, 2024", "-£120.00")
                        UpcomingBillRow("Internet Bill",    "May 28, 2024", "-£60.00")
                    }
                }
            }
        }
    }
}

// ── Chart Composables ────────────────────────────────────────────────────────

@Composable
private fun DonutChart(
    categories: List<SpendingCategory>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        var startAngle = -90f

        categories.forEach { cat ->
            val sweep = cat.percent * 360f
            drawArc(
                color = cat.color,
                startAngle = startAngle,
                sweepAngle = sweep - 2f,  // small gap between segments
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun LineChart(
    data: List<MonthlyPoint>,
    modifier: Modifier = Modifier
) {
    val incomeColor = Color(0xFF16A34A)
    val expensesColor = Color(0xFFEF4444)

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxVal = data.maxOf { maxOf(it.income, it.expenses) } * 1.1f
        val minVal = 0f
        val range = maxVal - minVal
        val stepX = size.width / (data.size - 1).toFloat()
        val paddingTop = 16.dp.toPx()
        val paddingBottom = 24.dp.toPx()
        val chartHeight = size.height - paddingTop - paddingBottom

        fun xFor(index: Int) = index * stepX
        fun yFor(value: Float) = paddingTop + chartHeight * (1f - (value - minVal) / range)

        // Draw gridlines
        repeat(4) { i ->
            val y = paddingTop + chartHeight * (i / 3f)
            drawLine(
                color = Color(0x22888888),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw income line
        val incomePath = Path()
        data.forEachIndexed { i, point ->
            val x = xFor(i)
            val y = yFor(point.income)
            if (i == 0) incomePath.moveTo(x, y) else incomePath.lineTo(x, y)
        }
        drawPath(incomePath, color = incomeColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Draw expenses line
        val expensesPath = Path()
        data.forEachIndexed { i, point ->
            val x = xFor(i)
            val y = yFor(point.expenses)
            if (i == 0) expensesPath.moveTo(x, y) else expensesPath.lineTo(x, y)
        }
        drawPath(expensesPath, color = expensesColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Draw dots on income line
        data.forEachIndexed { i, point ->
            drawCircle(color = incomeColor, radius = 4.dp.toPx(),
                center = Offset(xFor(i), yFor(point.income)))
            drawCircle(color = expensesColor, radius = 4.dp.toPx(),
                center = Offset(xFor(i), yFor(point.expenses)))
        }
    }

    // Month labels below chart
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        data.forEach { point ->
            Text(
                text = point.month,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BarChart(
    data: List<MonthlyPoint>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxVal = data.maxOf { it.expenses } * 1.2f

    Canvas(modifier = modifier) {
        val paddingBottom = 20.dp.toPx()
        val paddingTop = 8.dp.toPx()
        val chartHeight = size.height - paddingBottom - paddingTop
        val totalBars = data.size
        val barWidth = (size.width / totalBars) * 0.5f
        val gap = (size.width / totalBars) * 0.5f

        data.forEachIndexed { i, point ->
            val barHeight = (point.expenses / maxVal) * chartHeight
            val x = i * (barWidth + gap) + gap / 2f
            val y = paddingTop + chartHeight - barHeight

            drawRoundRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    }

    // Month labels
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        data.forEach { point ->
            Text(
                text = point.month,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Reusable small composables ───────────────────────────────────────────────

@Composable
private fun BudgetProgressCard() {
    var budgets by remember { mutableStateOf(hardcodedBudgets) }

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Budget Progress")

            if (budgets.isEmpty()) {
                // Empty state — coloured filled box with centred + button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { /* TODO: open add budget form */ },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        ) {
                            Text(
                                text = "+",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Light
                            )
                        }
                        Text(
                            text = "Add a budget",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                budgets.forEach { budget ->
                    BudgetProgressRow(budget)
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { /* TODO: open add budget form */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Budget", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    change: String,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            if (change.isNotEmpty()) {
                Text(change, style = MaterialTheme.typography.bodySmall,
                    color = if (positive) Color(0xFF16A34A) else Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CategoryLegendRow(cat: SpendingCategory) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).background(cat.color, CircleShape))
        Text("${cat.name} ${(cat.percent * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall)
        Text(cat.amount, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetProgressRow(budget: BudgetItem) {
    val progress = budget.spent / budget.total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(budget.category, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
            Text("£${budget.spent.toInt()} / £${budget.total.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = budget.color,
            trackColor = budget.color.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun TransactionRow(tx: Transaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(tx.date, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = tx.amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (tx.isIncome) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UpcomingBillRow(name: String, date: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
            Text(date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(amount, style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
    }
}