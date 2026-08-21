package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.smart_finance_app.budget.BudgetApi
import com.smart_finance_app.budget.BudgetData
import com.smart_finance_app.budget.BudgetRequest
import com.smart_finance_app.budget.BudgetResult
import com.smart_finance_app.budget.AddBudgetDialog
import com.smart_finance_app.budget.CompactBudgetProgressRow
import com.smart_finance_app.budget.computeBudgetsWithSpending
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
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
import smart_finance_app.shared.generated.resources.add


data class SpendingCategory(val name: String, val percent: Float, val amount: String, val color: Color)
data class BudgetItem(val category: String, val spent: Float, val total: Float, val color: Color)
data class MonthlyPoint(val month: String, val income: Float, val expenses: Float)
data class Transaction(val name: String, val date: String, val amount: String, val isIncome: Boolean)
data class AccountOverview(val bankName: String, val maskedNumber: String, val balance: String)

// ── Chart card catalogue ──────────────────────────────────────────────────────

enum class CardSize { FULL, HALF }

data class ChartCardDef(
    val key: String,
    val title: String,
    val description: String,
    val size: CardSize
)

/** All chart cards available to add via the + Charts sheet. */
val ALL_CHART_CARDS = listOf(
    ChartCardDef("weekly_spending",     "Weekly Spending",            "Day-by-day bar chart of your spending this week.",             CardSize.FULL),
    ChartCardDef("bank_comparison",     "Bank Account Comparison",    "Monthly spending per bank account, side by side.",             CardSize.HALF),
    ChartCardDef("time_of_day",         "Spending by Time of Day",    "Morning, afternoon and night breakdown as a donut chart.",     CardSize.HALF),
    ChartCardDef("largest_tx",          "Largest Transactions",       "Top 5 biggest outgoing transactions this month.",              CardSize.HALF),
    ChartCardDef("smallest_tx",         "Smallest Transactions",      "Top 5 smallest outgoing transactions this month.",             CardSize.HALF),
    ChartCardDef("merchant_frequency",  "Merchant Frequency",         "Your 5 most-visited merchants this month as a bubble chart.", CardSize.FULL)
)

/** Built-in cards always start on the dashboard (never in the + Charts sheet). */
private val BUILTIN_CARD_KEYS = setOf("spending", "trend", "top_categories", "budget")

/** Swaps two elements in a MutableList by index. */
private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

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
fun DashboardScreen(
    authToken: String,
    userName: String,
    transactions: List<TransactionData>,
    onConnectAccountClicked: () -> Unit,
    onViewAllTransactionsClicked: () -> Unit,
    api: DashboardApi,
    budgetApi: BudgetApi
) {
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

        state     = computeDashboardState(balances, transactions, accounts)
        isLoading = false
    }

    LaunchedEffect(authToken, transactions) { load() }


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
                        authToken = authToken,
                        spendingPeriod = spendingPeriod,
                        selectedAccounts = selectedAccounts,
                        onAccountsChanged = { selectedAccounts = it },
                        onPeriodSelected = { spendingPeriod = it },
                        onViewAllTransactionsClicked = onViewAllTransactionsClicked,
                        budgetApi = budgetApi
                    )
                } else {
                    DesktopDashboard(
                        state = state!!,
                        userName = userName,
                        authToken = authToken,
                        spendingPeriod = spendingPeriod,
                        selectedAccounts = selectedAccounts,
                        onAccountsChanged = { selectedAccounts = it },
                        onPeriodSelected = { spendingPeriod = it },
                        onViewAllTransactionsClicked = onViewAllTransactionsClicked,
                        budgetApi = budgetApi
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
    budgetApi: BudgetApi,
    authToken: String,
    spendingPeriod: SpendingPeriod,
    selectedAccounts: Set<String>,
    onAccountsChanged: (Set<String>) -> Unit,
    onPeriodSelected: (SpendingPeriod) -> Unit,
    onViewAllTransactionsClicked: () -> Unit
) {
    val greeting = rememberGreeting()
    val accountOptions = state.accounts.map { it.bankName }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var isCustomizing by remember { mutableStateOf(false) }
    var showChartsSheet by remember { mutableStateOf(false) }
    // deletedCards: built-in card keys removed from dashboard
    var deletedCards by remember { mutableStateOf(setOf<String>()) }
    // cardOrder: ordered slot keys on dashboard; "trend|top_categories" = half-size pair
    val cardOrder = remember {
        mutableStateListOf("spending", "trend|top_categories", "budget")
    }
    // chart cards from the + Charts sheet currently on the dashboard
    var chartCardsOnDashboard by remember { mutableStateOf(setOf<String>()) }

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

    @OptIn(ExperimentalMaterial3Api::class)
    if (showChartsSheet) {
        ChartsBottomSheet(
            chartCardsOnDashboard = chartCardsOnDashboard,
            onAddChart = { key ->
                chartCardsOnDashboard = chartCardsOnDashboard + key
                cardOrder.add(cardOrder.size, key)
                showChartsSheet = false
            },
            onDismiss = { showChartsSheet = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp)
    ) {
        // Header block: greeting, subtitle, and controls all tightly grouped
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$greeting, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Here's your financial overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardCustomizeButton(
                        isCustomizing = isCustomizing,
                        onClick = { isCustomizing = !isCustomizing }
                    )
                    Box {
                        OutlinedButton(
                            onClick = { accountDropdownExpanded = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
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
        }

        // ── Merged Balance + Income + Expenses card (fixed, not deletable/movable) ──
        item {
            FinancialOverviewCard(
                balance = formatCurrency(displayBalance, getCurrencySymbol(state.currency)),
                balanceTrend = state.balanceChangePercent,
                income = formatCurrency(state.monthlyIncome, getCurrencySymbol(state.currency)),
                incomeTrend = state.incomeChangePercent,
                expenses = formatCurrency(state.monthlyExpenses, getCurrencySymbol(state.currency)),
                expensesTrend = state.expenseChangePercent
            )
        }

        // ── Dynamic card list ─────────────────────────────────────────────────
        itemsIndexed(cardOrder, key = { _, key -> key }) { index, slotKey ->
            if (slotKey.contains('|')) {
                // Half-size pair slot (e.g. "trend|top_categories")
                val keys = slotKey.split('|')
                val visible = keys.filter { it !in deletedCards }
                if (visible.isNotEmpty()) {
                    if (visible.size == 1) {
                        val k = visible[0]
                        CustomizableCard(
                            cardKey       = k,
                            isCustomizing = isCustomizing,
                            onDelete      = { deletedCards = deletedCards + it },
                            onMoveUp      = { if (index > 0) cardOrder.move(index, index - 1) },
                            onMoveDown    = { if (index < cardOrder.lastIndex) cardOrder.move(index, index + 1) }
                        ) { HalfCardContent(k, state) }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            visible.forEach { k ->
                                CustomizableCard(
                                    cardKey       = k,
                                    isCustomizing = isCustomizing,
                                    onDelete      = { deletedCards = deletedCards + it },
                                    onMoveUp      = { if (index > 0) cardOrder.move(index, index - 1) },
                                    onMoveDown    = { if (index < cardOrder.lastIndex) cardOrder.move(index, index + 1) },
                                    modifier      = Modifier.weight(1f)
                                ) { HalfCardContent(k, state) }
                            }
                        }
                    }
                }
            } else {
                val def = ALL_CHART_CARDS.find { it.key == slotKey }
                val isChartCard = def != null
                val visible = slotKey !in deletedCards && (!isChartCard || slotKey in chartCardsOnDashboard)
                if (visible) {
                    val isHalf = def?.size == CardSize.HALF
                    if (isHalf) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CustomizableCard(
                                cardKey       = slotKey,
                                isCustomizing = isCustomizing,
                                onDelete      = {
                                    chartCardsOnDashboard = chartCardsOnDashboard - it
                                    cardOrder.remove(it)
                                },
                                onMoveUp      = { if (index > 0) cardOrder.move(index, index - 1) },
                                onMoveDown    = { if (index < cardOrder.lastIndex) cardOrder.move(index, index + 1) },
                                modifier      = Modifier.weight(1f)
                            ) { ChartCardContent(slotKey, state, filteredRawTransactions) }
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        CustomizableCard(
                            cardKey       = slotKey,
                            isCustomizing = isCustomizing,
                            onDelete      = {
                                if (isChartCard) {
                                    chartCardsOnDashboard = chartCardsOnDashboard - it
                                    cardOrder.remove(it)
                                } else {
                                    deletedCards = deletedCards + it
                                }
                            },
                            onMoveUp      = { if (index > 0) cardOrder.move(index, index - 1) },
                            onMoveDown    = { if (index < cardOrder.lastIndex) cardOrder.move(index, index + 1) }
                        ) {
                            when (slotKey) {
                                "spending" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SpendingOverviewHeader(selectedPeriod = spendingPeriod, onPeriodSelected = onPeriodSelected)
                                        val cats = computeSpendingCategories(filteredRawTransactions, spendingPeriod, state.currency)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DonutChart(categories = cats, modifier = Modifier.size(120.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (cats.isEmpty()) {
                                                    Text("No spending data yet", style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else cats.forEach { CategoryLegendRow(it) }
                                            }
                                        }
                                    }
                                }
                                "budget" -> BudgetProgressCardContent(
                                    api = budgetApi, authToken = authToken,
                                    transactions = state.rawTransactions, currency = state.currency
                                )
                                else -> ChartCardContent(slotKey, state, filteredRawTransactions)
                            }
                        }
                    }
                }
            }
        }

        // + Charts button — always sits above Recent Transactions
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DashboardChartsButton(onClick = { showChartsSheet = true })
            }
        }

        // Recent Transactions — fixed, no customize icons
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
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onViewAllTransactionsClicked() }
                        )
                    }
                    if (state.recentTransactions.isEmpty()) {
                        Text("No transactions yet", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    budgetApi: BudgetApi,
    authToken: String,
    spendingPeriod: SpendingPeriod,
    selectedAccounts: Set<String>,
    onAccountsChanged: (Set<String>) -> Unit,
    onPeriodSelected: (SpendingPeriod) -> Unit,
    onViewAllTransactionsClicked: () -> Unit
) {
    val greeting = rememberGreeting()
    val accountOptions = state.accounts.map { it.bankName }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var isCustomizing by remember { mutableStateOf(false) }
    var showChartsSheet by remember { mutableStateOf(false) }
    var deletedCards by remember { mutableStateOf(setOf<String>()) }
    var chartCardsOnDashboard by remember { mutableStateOf(setOf<String>()) }

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

    @OptIn(ExperimentalMaterial3Api::class)
    if (showChartsSheet) {
        ChartsBottomSheet(
            chartCardsOnDashboard = chartCardsOnDashboard,
            onAddChart = { key ->
                chartCardsOnDashboard = chartCardsOnDashboard + key
                showChartsSheet = false
            },
            onDismiss = { showChartsSheet = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp)
    ) {
        // Header block: greeting, subtitle, and controls all tightly grouped
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$greeting, $userName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Here's your financial overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardCustomizeButton(
                        isCustomizing = isCustomizing,
                        onClick = { isCustomizing = !isCustomizing }
                    )
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
        }

        // ── Merged Balance + Income + Expenses card (fixed) ──
        item {
            FinancialOverviewCard(
                balance = formatCurrency(displayBalance, getCurrencySymbol(state.currency)),
                balanceTrend = state.balanceChangePercent,
                income = formatCurrency(state.monthlyIncome, getCurrencySymbol(state.currency)),
                incomeTrend = state.incomeChangePercent,
                expenses = formatCurrency(state.monthlyExpenses, getCurrencySymbol(state.currency)),
                expensesTrend = state.expenseChangePercent
            )
        }

        // Spending + Trend row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if ("spending" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "spending",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SpendingOverviewHeader(selectedPeriod = spendingPeriod, onPeriodSelected = onPeriodSelected)
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
                }
                if ("trend" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "trend",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
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
        }

        // Top Categories + Budget row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if ("top_categories" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "top_categories",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Monthly Spending Comparison")
                            BarChart(data = state.monthlyTopCategories, modifier = Modifier.fillMaxWidth().height(160.dp))
                        }
                    }
                }
                if ("budget" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "budget",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        BudgetProgressCardContent(
                            api = budgetApi,
                            authToken = authToken,
                            transactions = state.rawTransactions,
                            currency = state.currency
                        )
                    }
                }
            }
        }

        // + Charts button
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DashboardChartsButton(onClick = { showChartsSheet = true })
            }
        }

        // Transactions + Accounts + Quick Actions row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if ("transactions" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "transactions",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(1.4f).fillMaxHeight()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionTitle("Recent Transactions")
                                Text("View all", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onViewAllTransactionsClicked() })
                            }
                            if (state.recentTransactions.isEmpty()) {
                                Text("No transactions yet", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                state.recentTransactions.forEach { tx -> TransactionRow(tx) }
                            }
                        }
                    }
                }
                if ("accounts_overview" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "accounts_overview",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
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
                }
                if ("quick_actions" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "quick_actions",
                        isCustomizing = isCustomizing,
                        onDelete = { deletedCards = deletedCards + it },
                        modifier = Modifier.weight(0.8f).fillMaxHeight()
                    ) {
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
private fun BudgetProgressCardContent(
    authToken: String,
    transactions: List<TransactionData>,
    currency: String,
    api: BudgetApi
) {
    val scope  = rememberCoroutineScope()
    val symbol = getCurrencySymbol(currency)

    var budgets    by remember { mutableStateOf<List<BudgetData>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var editBudget by remember { mutableStateOf<BudgetData?>(null) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }

    val budgetsWithSpending by derivedStateOf {
        computeBudgetsWithSpending(budgets, transactions)
    }

    suspend fun loadBudgets() {
        when (val r = api.getBudgets(authToken)) {
            is BudgetResult.Success -> budgets = r.data
            is BudgetResult.Failure -> {errorMsg = r.message}
        }
    }

    LaunchedEffect(authToken, transactions) {
        loadBudgets()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Budget Progress")
        if (errorMsg != null) {
            Text(
                text = errorMsg ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDC2626)
            )
        }
        if (budgets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            editBudget = null
                            errorMsg = null
                            showDialog = true
                        },
                        modifier = Modifier.size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Text(
                            "+", fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Light
                        )
                    }
                    Text(
                        "Add a budget",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            // Show budgets with Edit and Delete icons
            budgetsWithSpending.take(3).forEach { item ->
                CompactBudgetProgressRow(
                    item = item,
                    symbol = symbol,
                    onEdit = {
                        editBudget = item.budget
                        errorMsg = null
                        showDialog = true
                    },
                    onDelete = {
                        scope.launch {
                            when (val res = api.deleteBudget(authToken, item.budget.id)) {
                                is BudgetResult.Success -> {
                                    errorMsg = null
                                    loadBudgets()
                                }

                                is BudgetResult.Failure -> {
                                    errorMsg = res.message
                                }
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    editBudget = null
                    errorMsg = null
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add Budget", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showDialog) {
        val currentEdit = editBudget
        AddBudgetDialog(
            existing       = currentEdit,
            symbol         = symbol,
            usedCategories = budgets.filter { currentEdit == null || it.id != currentEdit.id }
                .map { it.category },
            serverError    = errorMsg,
            onDismiss      = {
                showDialog = false
                editBudget = null
                errorMsg = null
            },
            onConfirm      = { category, amount, period ->
                scope.launch {
                    val result = if (currentEdit != null) {
                        api.updateBudget(authToken, currentEdit.id, amount, category, period)
                    } else {
                        api.createBudget(authToken, BudgetRequest(category, amount, period))
                    }
                    when (result) {
                        is BudgetResult.Success -> {
                            showDialog = false
                            editBudget = null
                            errorMsg = null
                            loadBudgets()
                        }
                        is BudgetResult.Failure -> {
                            errorMsg = result.message
                        }
                    }
                }
            }
        )
    }
}

// KMP-compatible 2dp formatter
private fun formatDp(value: Double): String {
    val abs  = kotlin.math.abs(value)
    val int  = abs.toLong()
    val dec  = kotlin.math.round((abs - int) * 100).toLong()
    return "$int.${dec.toString().padStart(2, '0')}"
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

// ── Futuristic merged balance/income/expense card ─────────────────────────────

@Composable
private fun FinancialOverviewCard(
    balance: String,
    balanceTrend: Float,
    income: String,
    incomeTrend: Float,
    expenses: String,
    expensesTrend: Float
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val glowColor   = accentColor.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Outer glow border using Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            val cornerR = 16.dp.toPx()
            val strokeW = 2.5.dp.toPx()
            // Outer glow layer
            drawRoundRect(
                color        = glowColor,
                size         = size,
                cornerRadius = CornerRadius(cornerR + 4.dp.toPx()),
                style        = Stroke(width = 8.dp.toPx())
            )
            // Crisp accent border
            drawRoundRect(
                color        = accentColor.copy(alpha = 0.7f),
                size         = size,
                cornerRadius = CornerRadius(cornerR),
                style        = Stroke(width = strokeW)
            )
        }

        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Current Balance (larger)
                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text  = "Current Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text  = balance,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TrendIndicator(percentageChange = balanceTrend)
                }

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(accentColor.copy(alpha = 0.25f))
                )

                // Right: Income + Expenses stacked
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Income
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF16A34A), CircleShape)
                            )
                            Text(
                                text  = "Monthly Income",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text  = income,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TrendIndicator(percentageChange = incomeTrend)
                    }

                    HorizontalDivider(color = accentColor.copy(alpha = 0.12f))

                    // Expenses
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            )
                            Text(
                                text  = "Monthly Expenses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text  = expenses,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TrendIndicator(percentageChange = expensesTrend)
                    }
                }
            }
        }
    }
}

// ── Customizable card wrapper (delete + move overlays) ────────────────────────

@Composable
private fun CustomizableCard(
    cardKey: String,
    isCustomizing: Boolean,
    onDelete: (String) -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    val swapThresholdPx = with(LocalDensity.current) { 200.dp.toPx() }

    Box(modifier = modifier) {
        DashboardCard(modifier = Modifier.fillMaxWidth(), content = content)

        if (isCustomizing) {
            // ── Delete button: top-right red minus-in-circle ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(24.dp)
                    .background(Color(0xFFEF4444), CircleShape)
                    .clickable { onDelete(cardKey) },
                contentAlignment = Alignment.Center
            ) {
                MinusIcon(modifier = Modifier.size(12.dp), color = Color.White)
            }

            // ── Move handle: bottom-left circle, long-press + drag to reorder ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-10).dp, y = 10.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart  = { dragAccumY = 0f },
                            onDrag       = { change, dragAmount ->
                                change.consume()
                                dragAccumY += dragAmount.y
                                when {
                                    dragAccumY >  swapThresholdPx -> { onMoveDown(); dragAccumY = 0f }
                                    dragAccumY < -swapThresholdPx -> { onMoveUp();   dragAccumY = 0f }
                                }
                            },
                            onDragEnd    = { dragAccumY = 0f },
                            onDragCancel = { dragAccumY = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                MoveIcon(modifier = Modifier.size(12.dp), color = MaterialTheme.colorScheme.surface)
            }
        }
    }
}

// ── Four-arrow move icon drawn with Canvas ────────────────────────────────────

@Composable
private fun MoveIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = size.minDimension * 0.28f
        val head = size.minDimension * 0.14f
        val sw = 1.8.dp.toPx()

        // Horizontal line
        drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = sw, cap = StrokeCap.Round)
        // Vertical line
        drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = sw, cap = StrokeCap.Round)

        // Arrow heads (left, right, up, down)
        // Left
        drawLine(color, Offset(cx - arm, cy), Offset(cx - arm + head, cy - head), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - arm, cy), Offset(cx - arm + head, cy + head), strokeWidth = sw, cap = StrokeCap.Round)
        // Right
        drawLine(color, Offset(cx + arm, cy), Offset(cx + arm - head, cy - head), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + arm, cy), Offset(cx + arm - head, cy + head), strokeWidth = sw, cap = StrokeCap.Round)
        // Up
        drawLine(color, Offset(cx, cy - arm), Offset(cx - head, cy - arm + head), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx, cy - arm), Offset(cx + head, cy - arm + head), strokeWidth = sw, cap = StrokeCap.Round)
        // Down
        drawLine(color, Offset(cx, cy + arm), Offset(cx - head, cy + arm - head), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx, cy + arm), Offset(cx + head, cy + arm - head), strokeWidth = sw, cap = StrokeCap.Round)
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

@Composable
private fun DashboardChartsButton(
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.add),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Charts",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DashboardCustomizeButton(
    isCustomizing: Boolean,
    onClick: () -> Unit
) {
    if (isCustomizing) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.check),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = "Customise",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Half-size built-in card content ──────────────────────────────────────────

@Composable
private fun ColumnScope.HalfCardContent(
    cardKey: String,
    state: DashboardState
) {
    when (cardKey) {
        "trend" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Monthly Trend")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(color = Color(0xFF16A34A), label = "In")
                    LegendDot(color = Color(0xFFEF4444), label = "Out")
                }
                LineChart(data = state.monthlyTrend, modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
        "top_categories" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Highest Spending")
                BarChart(data = state.monthlyTopCategories, modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
    }
}

// ── + Charts card content dispatcher ─────────────────────────────────────────

@Composable
private fun ColumnScope.ChartCardContent(
    cardKey: String,
    state: DashboardState,
    rawTransactions: List<TransactionData>
) {
    val sym = getCurrencySymbol(state.currency)
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    when (cardKey) {

        // ── Weekly Spending (full) ──
        "weekly_spending" -> {
            val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
            val dayOfWeek = now.dayOfWeek.ordinal // Mon=0
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
                    .sumOf { kotlin.math.abs(it.amount) }.toFloat()
                MonthlyTopCategory(month = dayLabel, category = "Spending", amount = total, color = Color(0xFF6366F1))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Weekly Spending")
                BarChart(data = weeklyData, modifier = Modifier.fillMaxWidth().height(140.dp))
            }
        }

        // ── Bank Account Comparison (half) ──
        "bank_comparison" -> {
            val accountSpend = state.accounts.map { acc ->
                val total = rawTransactions
                    .filter { tx ->
                        val p = tx.timestamp.take(10).split("-")
                        p.size == 3 &&
                                p[0].toIntOrNull() == now.year &&
                                p[1].toIntOrNull() == now.month.number &&
                                tx.amount < 0
                    }
                    .sumOf { kotlin.math.abs(it.amount) }.toFloat()
                acc.bankName to total
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Bank Comparison")
                accountSpend.forEachIndexed { i, (name, amount) ->
                    val maxAmt = accountSpend.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1f
                    val fraction = (amount / maxAmt).coerceIn(0f, 1f)
                    val barColors = listOf(Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEC4899))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.weight(1f).height(10.dp).background(
                                MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)
                            )) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                                    .background(barColors[i % barColors.size], RoundedCornerShape(5.dp)))
                            }
                            Text(formatCurrency(amount.toDouble(), sym),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── Spending by Time of Day (half) ──
        "time_of_day" -> {
            val buckets = mapOf("Morning" to Color(0xFFF59E0B), "Afternoon" to Color(0xFF6366F1), "Night" to Color(0xFF1E40AF))
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
                    when { hour < 12 -> "Morning"; hour < 18 -> "Afternoon"; else -> "Night" }
                }
            val total = grouped.values.flatten().sumOf { kotlin.math.abs(it.amount) }.takeIf { it > 0 } ?: 1.0
            val cats = buckets.map { (label, color) ->
                val amt = grouped[label]?.sumOf { kotlin.math.abs(it.amount) } ?: 0.0
                SpendingCategory(name = label, percent = (amt / total).toFloat(), amount = formatCurrency(amt, sym), color = color)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Time of Day")
                DonutChart(categories = cats, modifier = Modifier.size(90.dp).align(Alignment.CenterHorizontally))
                cats.forEach { cat ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(cat.color, CircleShape))
                        Text("${cat.name} ${(cat.percent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ── Largest Transactions (half) ──
        "largest_tx" -> {
            val top5 = rawTransactions
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .sortedBy { it.amount }
                .take(5)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("Largest Transactions")
                if (top5.isEmpty()) {
                    Text("No data", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    top5.forEachIndexed { i, tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(16.dp))
                                Text(
                                    text = tx.merchantName?.ifBlank { null } ?: tx.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(formatCurrency(kotlin.math.abs(tx.amount), sym),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        // ── Smallest Transactions (half) ──
        "smallest_tx" -> {
            val bottom5 = rawTransactions
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .sortedByDescending { it.amount }
                .take(5)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("Smallest Transactions")
                if (bottom5.isEmpty()) {
                    Text("No data", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    bottom5.forEachIndexed { i, tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(16.dp))
                                Text(
                                    text = tx.merchantName?.ifBlank { null } ?: tx.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(formatCurrency(kotlin.math.abs(tx.amount), sym),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── Merchant Frequency (full) ──
        "merchant_frequency" -> {
            val top5merchants = rawTransactions
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .groupBy { tx -> tx.merchantName?.ifBlank { null } ?: tx.description }
                .map { (name, txList) -> name to txList.size }
                .sortedByDescending { it.second }
                .take(5)
            val maxCount = top5merchants.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1
            val bubbleColors = listOf(
                Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF59E0B),
                Color(0xFFEC4899), Color(0xFF3B82F6)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Merchant Frequency")
                if (top5merchants.isEmpty()) {
                    Text("No data this month", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Bubble chart: horizontal row of circles sized by frequency
                    Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        top5merchants.forEachIndexed { i, (name, count) ->
                            val fraction = count.toFloat() / maxCount
                            val bubbleSize = (32 + 52 * fraction).dp
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.size(bubbleSize)
                                        .background(bubbleColors[i].copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${count}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    // Labels beneath bubbles
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        top5merchants.forEach { (name, _) ->
                            Text(
                                text = name.take(8),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── + Charts bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsBottomSheet(
    chartCardsOnDashboard: Set<String>,
    onAddChart: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available = ALL_CHART_CARDS.filter { it.key !in chartCardsOnDashboard }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Charts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    MinusIcon(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (available.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        text = "All charts are already on your dashboard.\nRemove one with the − button to free up a slot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    available.forEach { def ->
                        ChartOptionRow(def = def, onAdd = { onAddChart(def.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartOptionRow(
    def: ChartCardDef,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = def.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (def.size == CardSize.FULL) Color(0xFF6366F1).copy(alpha = 0.15f)
                        else Color(0xFF22C55E).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (def.size == CardSize.FULL) "Full" else "Half",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (def.size == CardSize.FULL) Color(0xFF6366F1) else Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = def.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("+ Add", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MinusIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, size.height * 0.5f),
            end = Offset(size.width * 0.8f, size.height * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}