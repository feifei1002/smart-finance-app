package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.arrow_upward
import smart_finance_app.shared.generated.resources.arrow_downward
import smart_finance_app.shared.generated.resources.calendar_month
import smart_finance_app.shared.generated.resources.arrow_drop_down
import smart_finance_app.shared.generated.resources.bank
import smart_finance_app.shared.generated.resources.check
import com.smart_finance_app.accounts.ConnectBankAccountScreen
import androidx.compose.ui.text.drawText


data class SpendingCategory(val name: String, val percent: Float, val amount: String, val color: Color)
data class BudgetItem(val category: String, val spent: Float, val total: Float, val color: Color)
data class MonthlyPoint(val month: String, val income: Float, val expenses: Float)
data class Transaction(val name: String, val date: String, val amount: String, val isIncome: Boolean)
data class AccountOverview(val bankName: String, val maskedNumber: String, val balance: String)

@Composable
private fun rememberGreeting(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = now.hour
    return when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else      -> "Good night"
    }
}

@Composable
fun DashboardScreen(apiBaseUrl: String, authToken: String, userName: String,onConnectAccountClicked: () -> Unit ) {
    val api   = remember(apiBaseUrl) { DashboardApi(apiBaseUrl) }
    val scope = rememberCoroutineScope()

    var state          by remember { mutableStateOf<DashboardState?>(null) }
    var isLoading      by remember { mutableStateOf(true) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }
    var spendingPeriod by remember { mutableStateOf(SpendingPeriod.THIS_MONTH) }
    var selectedAccounts by remember { mutableStateOf(setOf<String>()) }

    suspend fun load() {
        isLoading = true
        errorMsg  = null

        val a = api.getAccounts(authToken)
        if (a is DashboardResult.Failure) { errorMsg = a.message; isLoading = false; return }
        val accounts = (a as DashboardResult.Success).data

        if (accounts.isEmpty()) {
            state     = null
            isLoading = false
            return
        }

        val b = api.getBalances(authToken)
        if (b is DashboardResult.Failure) { errorMsg = b.message; isLoading = false; return }
        val balances = (b as DashboardResult.Success).data

        // Transactions are non-critical — if they fail, show dashboard with empty list
        val t = api.getTransactions(authToken)
        val transactions = if (t is DashboardResult.Success) t.data else emptyList()

        state     = computeDashboardState(balances, transactions, accounts)
        isLoading = false
    }

    LaunchedEffect(authToken) { load() }

    DisposableEffect(api) { onDispose { api.close() } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading your financial data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)) {
                        Text("Something went wrong",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(errorMsg ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Button(onClick = { scope.launch { load() } }) { Text("Retry") }
                    }
                }
            }
            state == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)) {
                        Icon(
                            painter = painterResource(Res.drawable.bank),
                            contentDescription = "Bank Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("No accounts connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Connect a bank account to see your\nfinancial overview here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { onConnectAccountClicked() },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Connect Account",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            // Replace the old layout branch with this:
            else -> {
                val compact = maxWidth < 700.dp
                if (compact) {
                    MobileDashboard(
                        state = state!!,
                        userName = userName,
                        spendingPeriod = spendingPeriod,
                        selectedAccounts = selectedAccounts, // Pass it down
                        onAccountsChanged = { selectedAccounts = it }, // Callback to update state
                        onPeriodSelected = { spendingPeriod = it }
                    )
                } else {
                    DesktopDashboard(
                        state = state!!,
                        userName = userName,
                        spendingPeriod = spendingPeriod,
                        selectedAccounts = selectedAccounts,
                        onAccountsChanged = { selectedAccounts = it },
                        onPeriodSelected = { spendingPeriod = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileDashboard(
    state: DashboardState,
    userName: String,
    spendingPeriod: SpendingPeriod,
    selectedAccounts: Set<String>,
    onAccountsChanged: (Set<String>) -> Unit,
    onPeriodSelected: (SpendingPeriod) -> Unit
) {
    val greeting = rememberGreeting()
    val accountOptions = state.accounts.map { it.bankName }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    // ── 1. FILTERED BALANCES & ACCOUNTS ──
    val activeAccounts = remember(selectedAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.accounts
        else state.accounts.filter { it.bankName in selectedAccounts }
    }

    // Calculates display balance based on selected account(s)
    val displayBalance = remember(activeAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.currentBalance
        else {
            state.accounts
                .filter { it.bankName in selectedAccounts }
                .sumOf { it.balance.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0 }
        }
    }

    // ── 2. FILTERED RAW TRANSACTIONS ──
    val filteredRawTransactions = remember(selectedAccounts, state.rawTransactions) {
        if (selectedAccounts.isEmpty()) state.rawTransactions
        else state.rawTransactions.filter { tx ->
            state.accounts.find { it.bankName in selectedAccounts } != null
        }
    }

    // Label on the top-right button
    val selectorLabel = if (selectedAccounts.isEmpty()) "All Accounts"
    else if (selectedAccounts.size == 1) selectedAccounts.first()
    else "${selectedAccounts.size} accounts"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$greeting, $userName ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Here's your financial overview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Account selector dropdown
                Box {
                    OutlinedButton(
                        onClick = { accountDropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(selectorLabel, style = MaterialTheme.typography.labelSmall)
                        Icon(
                            painter = painterResource(Res.drawable.arrow_drop_down),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        // "All Accounts" option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedAccounts.isEmpty(),
                                        onCheckedChange = { onAccountsChanged(setOf()) }
                                    )
                                    Text("All Accounts", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                onAccountsChanged(setOf())
                                accountDropdownExpanded = false
                            }
                        )
                        HorizontalDivider()

                        // Individual Account options
                        accountOptions.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = account in selectedAccounts,
                                            onCheckedChange = { checked ->
                                                val next = if (checked) selectedAccounts + account else selectedAccounts - account
                                                onAccountsChanged(next)
                                            }
                                        )
                                        Text(account, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    val next = if (account in selectedAccounts) selectedAccounts - account else selectedAccounts + account
                                    onAccountsChanged(next)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Current Balance Card (Uses dynamic displayBalance)
        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Current Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(displayBalance, getCurrencySymbol(state.currency)),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TrendIndicator(percentageChange = state.balanceChangePercent)
                }
            }
        }

        // Monthly Income & Expenses Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Monthly Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCurrency(state.monthlyIncome, getCurrencySymbol(state.currency)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TrendIndicator(percentageChange = state.incomeChangePercent)
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Monthly Expenses",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCurrency(state.monthlyExpenses, getCurrencySymbol(state.currency)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TrendIndicator(percentageChange = state.expenseChangePercent)
                    }
                }
            }
        }

        // Spending Overview Card (Filters categories based on filteredRawTransactions)
        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpendingOverviewHeader(selectedPeriod = spendingPeriod, onPeriodSelected = onPeriodSelected)

                    val filteredCategories = computeSpendingCategories(
                        transactions = filteredRawTransactions,
                        period = spendingPeriod,
                        currency = state.currency
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonutChart(categories = filteredCategories, modifier = Modifier.size(120.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (filteredCategories.isEmpty()) {
                                Text(
                                    text = "No spending data yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                filteredCategories.forEach { cat -> CategoryLegendRow(cat) }
                            }
                        }
                    }
                }
            }
        }

        // Charts Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Monthly Trend")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LegendDot(color = Color(0xFF16A34A), label = "In")
                            LegendDot(color = Color(0xFFEF4444), label = "Out")
                        }
                        LineChart(data = state.monthlyTrend, modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Highest Spending")
                        BarChart(data = state.monthlyTopCategories, modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }
        }

        item { BudgetProgressCard() }

        // Recent Transactions Card
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
                    if (state.recentTransactions.isEmpty()) {
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.recentTransactions.forEach { tx -> TransactionRow(tx) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopDashboard(
    state: DashboardState,
    userName: String,
    spendingPeriod: SpendingPeriod,
    selectedAccounts: Set<String>,
    onAccountsChanged: (Set<String>) -> Unit,
    onPeriodSelected: (SpendingPeriod) -> Unit
) {
    val greeting = rememberGreeting()
    val accountOptions = state.accounts.map { it.bankName }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    // ── 1. FILTERED BALANCES & ACCOUNTS ──
    val activeAccounts = remember(selectedAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.accounts
        else state.accounts.filter { it.bankName in selectedAccounts }
    }

    // Calculates display balance based on selected account(s)
    val displayBalance = remember(activeAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.currentBalance
        else {
            state.accounts
                .filter { it.bankName in selectedAccounts }
                .sumOf { it.balance.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0 }
        }
    }

    // ── 2. FILTERED RAW TRANSACTIONS ──
    val filteredRawTransactions = remember(selectedAccounts, state.rawTransactions) {
        if (selectedAccounts.isEmpty()) state.rawTransactions
        else state.rawTransactions.filter { tx ->
            state.accounts.find { it.bankName in selectedAccounts } != null
        }
    }

    val selectorLabel = if (selectedAccounts.isEmpty()) "All Accounts"
    else if (selectedAccounts.size == 1) selectedAccounts.first()
    else "${selectedAccounts.size} accounts"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$greeting, $userName ",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Here's your financial overview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    OutlinedButton(onClick = { accountDropdownExpanded = true }) {
                        Text(selectorLabel, style = MaterialTheme.typography.labelMedium)
                        Icon(
                            painter = painterResource(Res.drawable.arrow_drop_down),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedAccounts.isEmpty(),
                                        onCheckedChange = { onAccountsChanged(setOf()) }
                                    )
                                    Text("All Accounts", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                onAccountsChanged(setOf())
                                accountDropdownExpanded = false
                            }
                        )
                        HorizontalDivider()
                        accountOptions.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = account in selectedAccounts,
                                            onCheckedChange = { checked ->
                                                val next = if (checked) selectedAccounts + account else selectedAccounts - account
                                                onAccountsChanged(next)
                                            }
                                        )
                                        Text(account, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    val next = if (account in selectedAccounts) selectedAccounts - account else selectedAccounts + account
                                    onAccountsChanged(next)
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val sym = getCurrencySymbol(state.currency)

                StatCard(
                    label = "Current Balance",
                    value = formatCurrency(displayBalance, sym), // Uses dynamic display balance!
                    trendPercentage = state.balanceChangePercent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                StatCard(
                    label = "Monthly Income",
                    value = formatCurrency(state.monthlyIncome, sym),
                    trendPercentage = state.incomeChangePercent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                StatCard(
                    label = "Monthly Expenses",
                    value = formatCurrency(state.monthlyExpenses, sym),
                    trendPercentage = state.expenseChangePercent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                // Net Savings trend calculated dynamically
                val netSavingsTrend = state.incomeChangePercent - state.expenseChangePercent
                StatCard(
                    label = "Net Savings",
                    value = formatCurrency(state.monthlyIncome - state.monthlyExpenses, sym),
                    trendPercentage = netSavingsTrend,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpendingOverviewHeader(selectedPeriod = spendingPeriod, onPeriodSelected = onPeriodSelected)

                        // Uses filteredRawTransactions for dynamic Donut Chart filtering
                        val filteredCategories = computeSpendingCategories(
                            transactions = filteredRawTransactions,
                            period = spendingPeriod,
                            currency = state.currency
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DonutChart(categories = filteredCategories, modifier = Modifier.size(140.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (filteredCategories.isEmpty()) {
                                    Text("No spending data yet", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    filteredCategories.forEach { cat ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.size(10.dp).background(cat.color, CircleShape))
                                            Text("${cat.name}  ${(cat.percent * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall)
                                            Text(cat.amount, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Monthly Trend")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendDot(color = Color(0xFF16A34A), label = "In")
                            LegendDot(color = Color(0xFFEF4444), label = "Out")
                        }
                        LineChart(data = state.monthlyTrend, modifier = Modifier.fillMaxWidth().height(160.dp))
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Monthly Spending Comparison")
                        BarChart(data = state.monthlyTopCategories, modifier = Modifier.fillMaxWidth().height(160.dp))
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BudgetProgressCard()
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        if (state.recentTransactions.isEmpty()) {
                            Text("No transactions yet", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.recentTransactions.forEach { tx -> TransactionRow(tx) }
                        }
                    }
                }
                DashboardCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Accounts Overview")
                        if (state.accounts.isEmpty()) {
                            Text("No accounts connected yet", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.accounts.forEach { account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(account.bankName, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium)
                                        Text("**** ${account.maskedNumber}", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(account.balance, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        Text("Connected", style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF16A34A))
                                    }
                                }
                                if (account != state.accounts.last()) HorizontalDivider()
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Add Account", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
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
                        Text("Coming soon", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


@Composable
private fun DonutChart(categories: List<SpendingCategory>, modifier: Modifier = Modifier) {
    if (categories.isEmpty()) return
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        var startAngle = -90f
        categories.forEach { cat ->
            val sweep = cat.percent * 360f
            // Only draw if sweep is large enough to be visible
            if (sweep > 3f) {
                drawArc(
                    color = cat.color,
                    startAngle = startAngle,
                    sweepAngle = sweep - 2f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }
            startAngle += sweep
        }
    }
}

@Composable
private fun LineChart(data: List<MonthlyPoint>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val incomeColor   = Color(0xFF16A34A)
    val expensesColor = Color(0xFFEF4444)
    val maxVal = data.maxOf { maxOf(it.income, it.expenses) } * 1.2f
    val yLabels = (3 downTo 0).map { i -> (maxVal * i / 3).toInt() }

    // CRITICAL CHANGES: Tightened down the left column layout footprints
    // to physically draw the gridlines and trend lines closer to the left wall.
    val labelWidth = 16.dp // Drastically reduced from 32.dp to eliminate dead workspace
    val spacingGutter = 4.dp // Halved from 8.dp to snap the chart lines leftwards

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Y-axis labels column - tightly fits numbers, hugging the far left
            Column(
                modifier = Modifier
                    .width(labelWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                yLabels.forEach { v ->
                    Text(
                        text = if (v >= 1000) "${v / 1000}k" else "$v",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacingGutter))

            // The main graphic canvas - now pulled way over to the left!
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                val stepX = chartWidth / (data.size - 1).toFloat()
                fun xFor(i: Int) = i * stepX
                fun yFor(v: Float) = chartHeight * (1f - v.coerceIn(0f, maxVal) / maxVal)

                // Render matching grid lines
                repeat(4) { i ->
                    val y = chartHeight * (i / 3f)
                    drawLine(
                        color = Color(0x1F888888),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Income trend line
                val incomePath = Path()
                data.forEachIndexed { i, p ->
                    if (i == 0) incomePath.moveTo(xFor(i), yFor(p.income))
                    else incomePath.lineTo(xFor(i), yFor(p.income))
                }
                drawPath(incomePath, incomeColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                // Expenses trend line
                val expPath = Path()
                data.forEachIndexed { i, p ->
                    if (i == 0) expPath.moveTo(xFor(i), yFor(p.expenses))
                    else expPath.lineTo(xFor(i), yFor(p.expenses))
                }
                drawPath(expPath, expensesColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                // Vector point circles matching line vertices
                data.forEachIndexed { i, p ->
                    drawCircle(incomeColor, 3.5.dp.toPx(), Offset(xFor(i), yFor(p.income)))
                    drawCircle(expensesColor, 3.5.dp.toPx(), Offset(xFor(i), yFor(p.expenses)))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Month items automatically tracking the new left-shifted line anchor bounds
        val monthNumberMap = mapOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4,
            "May" to 5, "Jun" to 6, "Jul" to 7, "Aug" to 8,
            "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = labelWidth + spacingGutter), // Dynamically stays aligned with the canvas start point
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach {
                Text(
                    text = "${monthNumberMap[it.month] ?: it.month}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(IntrinsicSize.Min),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BarChart(data: List<MonthlyTopCategory>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No spending data yet", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val barWidthDp = 32.dp
    val gapDp      = 12.dp
    val totalWidth = (barWidthDp + gapDp) * data.size
    val maxVal     = data.maxOf { it.amount } * 1.2f
    val scrollState = rememberScrollState()

    Column {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.width(totalWidth).height(120.dp)) {
                val paddingTop    = 20.dp.toPx()  // space so tallest bar doesn't touch title
                val paddingBottom = 8.dp.toPx()
                val chartHeight   = size.height - paddingTop - paddingBottom
                val bw  = barWidthDp.toPx()
                val gap = gapDp.toPx()
                data.forEachIndexed { i, point ->
                    val barHeight = (point.amount / maxVal) * chartHeight
                    val x = i * (bw + gap) + gap / 2f
                    val y = paddingTop + chartHeight - barHeight
                    drawRoundRect(
                        color      = point.color.copy(alpha = 0.85f),
                        topLeft    = Offset(x, y),
                        size       = Size(bw, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
        // Month + category labels
        Row(
            modifier = Modifier.horizontalScroll(scrollState).width(totalWidth),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { point ->
                Column(
                    modifier = Modifier.width(barWidthDp + gapDp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(point.month, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Text(point.category, style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = point.color,
                        textAlign = TextAlign.Center,
                        maxLines = 1)
                }
            }
        }
    }
}


@Composable
private fun BudgetProgressCard() {
    var budgets by remember { mutableStateOf(emptyList<BudgetItem>()) }
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Budget Progress")
            if (budgets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { /* TODO: open add budget form */ },
                            modifier = Modifier.size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Text("+", fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Light)
                        }
                        Text("Add a budget", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                budgets.forEach { BudgetProgressRow(it) }
                Spacer(Modifier.height(4.dp))
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
private fun SpendingOverviewHeader(
    selectedPeriod: SpendingPeriod,
    onPeriodSelected: (SpendingPeriod) -> Unit
) {
    var periodDropdownExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle("Spending (${selectedPeriod.label})")
        Box {
            IconButton(
                onClick = { periodDropdownExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.calendar_month),
                    contentDescription = "Select period",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = periodDropdownExpanded,
                onDismissRequest = { periodDropdownExpanded = false }
            ) {
                SpendingPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (period == selectedPeriod) {
                                    Icon(
                                        painter = painterResource(Res.drawable.check),
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Spacer(Modifier.width(14.dp))
                                }
                                Text(period.label, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            onPeriodSelected(period)
                            periodDropdownExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    trendPercentage: Float, // Changed from raw strings to match backend pipeline
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Render the brand new trend row beautifully right under the amount
            TrendIndicator(percentageChange = trendPercentage)
        }
    }
}

@Composable
private fun ArrowChange(change: String, positive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(if (positive) Res.drawable.arrow_upward else Res.drawable.arrow_downward),
            contentDescription = null,
            tint = if (positive) Color(0xFF16A34A) else Color(0xFFEF4444),
            modifier = Modifier.size(14.dp)
        )
        Text(change, style = MaterialTheme.typography.bodySmall,
            color = if (positive) Color(0xFF16A34A) else Color(0xFFEF4444))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CategoryLegendRow(cat: SpendingCategory) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(cat.color, CircleShape))
        Text("${cat.name} ${(cat.percent * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        Text(cat.amount, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetProgressRow(budget: BudgetItem) {
    val progress = budget.spent / budget.total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(budget.category, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tx.date, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tx.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (tx.isIncome) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TrendIndicator(percentageChange: Float) {
    val isPositive = percentageChange >= 0f
    val absValue = kotlin.math.abs(percentageChange)
    val color = if (isPositive) Color(0xFF16A34A) else Color(0xFFEF4444)
    val icon = if (isPositive) Res.drawable.arrow_upward else Res.drawable.arrow_downward

    // KMP-safe rounding to 1 decimal place without JVM String.format()
    val formattedPercentage = remember(absValue) {
        val intPart = absValue.toLong()
        val decPart = kotlin.math.round((absValue - intPart) * 10).toLong()
        "$intPart.$decPart"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "$formattedPercentage%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "vs last month",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UpcomingBillRow(name: String, date: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(amount, style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
    }
}