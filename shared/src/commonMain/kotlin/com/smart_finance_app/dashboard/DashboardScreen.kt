package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.launch
import com.smart_finance_app.budget.BudgetApi
import com.smart_finance_app.budget.BudgetData
import com.smart_finance_app.budget.BudgetRequest
import com.smart_finance_app.budget.BudgetResult
import com.smart_finance_app.budget.AddBudgetDialog
import com.smart_finance_app.budget.CompactBudgetProgressRow
import com.smart_finance_app.budget.computeBudgetsWithSpending
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.arrow_upward
import smart_finance_app.shared.generated.resources.arrow_downward
import smart_finance_app.shared.generated.resources.calendar_month
import smart_finance_app.shared.generated.resources.arrow_drop_down
import smart_finance_app.shared.generated.resources.bank
import smart_finance_app.shared.generated.resources.check
import smart_finance_app.shared.generated.resources.add
import smart_finance_app.shared.generated.resources.drag_pan


data class SpendingCategory(val name: String, val percent: Float, val amount: String, val color: Color)
data class BudgetItem(val category: String, val spent: Float, val total: Float, val color: Color)
data class MonthlyPoint(val month: String, val income: Float, val expenses: Float)
data class Transaction(val name: String, val date: String, val amount: String, val isIncome: Boolean)
data class AccountOverview(val bankName: String, val maskedNumber: String, val balance: String)
data class InferredBill(val merchant: String, val amount: Double, val expectedDate: LocalDate, val cadence: String)

/**
 * Finds outgoing merchant payments that recur at a similar cadence and amount.
 * This deliberately uses only the transaction list already on-device.
 */
private fun inferUpcomingBills(transactions: List<TransactionData>): List<InferredBill> {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return transactions
        .filter { it.amount < 0 }
        .groupBy { it.merchantName?.takeIf(String::isNotBlank) ?: it.description }
        .mapNotNull { (merchant, entries) ->
            val dated = entries.mapNotNull { tx ->
                runCatching { LocalDate.parse(tx.timestamp.take(10)) }.getOrNull()?.let { it to abs(tx.amount) }
            }.sortedBy { it.first }
            if (dated.size < 2) return@mapNotNull null

            val gaps = dated.zipWithNext { first, second -> first.first.daysUntil(second.first) }
            val averageGap = gaps.average()
            val cadence = when {
                averageGap in 6.0..9.0 -> "Weekly"
                averageGap in 25.0..35.0 -> "Monthly"
                else -> return@mapNotNull null
            }
            if (gaps.any { kotlin.math.abs(it - averageGap) > if (cadence == "Weekly") 2 else 6 }) return@mapNotNull null

            val averageAmount = dated.map { it.second }.average()
            if (averageAmount <= 0.0 || dated.any { kotlin.math.abs(it.second - averageAmount) > averageAmount * 0.15 }) return@mapNotNull null

            val nextDate = dated.last().first.plus(DatePeriod(days = averageGap.roundToInt()))
            InferredBill(merchant, averageAmount, nextDate, cadence)
        }
        .filter { it.expectedDate >= today }
        .sortedBy { it.expectedDate }
        .take(6)
}

// ── Chart card catalogue ──────────────────────────────────────────────────────

enum class CardSize { FULL, HALF }

private fun isHalfCardKey(key: String): Boolean =
    ALL_CHART_CARDS.find { it.key == key }?.size == CardSize.HALF ||
            key == "trend" || key == "top_categories"

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
    ChartCardDef("merchant_frequency",  "Merchant Spending Treemap",  "Your top merchants this month shown as a spending treemap.",   CardSize.FULL),
    ChartCardDef("upcoming_bills",       "Upcoming Bills",             "Predicted recurring payments from your transaction history.",  CardSize.FULL)
)

/** Fixed height for every half-size card (side-by-side pair). */
private val HALF_CARD_HEIGHT = 200.dp

/** Fixed height for every full-size card. */
private val FULL_CARD_HEIGHT = 240.dp

/** Built-in cards always start on the dashboard (never in the + Charts sheet). */
private val BUILTIN_CARD_KEYS = setOf("spending", "trend", "top_categories", "budget")

/** Swaps two elements in a MutableList by index. */
private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

// ── Dashboard layout persistence (multiplatform-settings) ────────────────────

private val KEY_CARD_ORDER    = "card_order_v2"
private val KEY_DELETED_CARDS = "deleted_cards_v2"
private val KEY_CHART_CARDS   = "chart_cards_v2"
private val KEY_HALF_POSITIONS = "half_card_positions_v1"
private val KEY_MIGRATED      = "migrated_v2"          // set once after v1 cleanup
private val DEFAULT_CARD_ORDER = "spending,trend,top_categories,budget"

/** Encodes the card order list to a comma-separated string for storage. */
private fun List<String>.encodeOrder(): String = joinToString(",")

/** Decodes the card order string back to a list. */
private fun String.decodeOrder(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** Encodes a Set<String> to a pipe-separated string. */
private fun Set<String>.encodeSet(): String = joinToString("|")

/** Decodes a pipe-separated string to a Set<String>. */
private fun String.decodeSet(): Set<String> =
    split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

private fun Map<String, Float>.encodeHalfPositions(): String =
    entries.joinToString("|") { (key, position) -> "$key:${position.coerceIn(0f, 1f)}" }

private fun String.decodeHalfPositions(): Map<String, Float> = split("|").mapNotNull { entry ->
    val split = entry.lastIndexOf(':')
    if (split <= 0) null else entry.substring(0, split) to
            (entry.substring(split + 1).toFloatOrNull()?.coerceIn(0f, 1f) ?: return@mapNotNull null)
}.toMap()

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

    // Snapshot taken when entering customise mode — used to restore on Cancel
    data class LayoutSnapshot(
        val cardOrder: List<String>,
        val deletedCards: Set<String>,
        val chartCardsOnDashboard: Set<String>,
        val halfPositions: Map<String, Float>
    )
    var layoutSnapshot by remember { mutableStateOf<LayoutSnapshot?>(null) }

    // ── Persisted layout state (multiplatform-settings — synchronous) ─────────
    val settings = remember { Settings() }

    // One-time migration: wipe any v1 paired-slot data so we start clean
    remember {
        if (!settings.getBoolean(KEY_MIGRATED, false)) {
            settings.remove("card_order")
            settings.remove("deleted_cards")
            settings.remove("chart_cards_on_dashboard")
            // Also wipe v2 keys so state is fully fresh — all 6 charts go back to sheet
            settings.remove(KEY_CARD_ORDER)
            settings.remove(KEY_DELETED_CARDS)
            settings.remove(KEY_CHART_CARDS)
            settings.putBoolean(KEY_MIGRATED, true)
        }
    }

    var chartCardsOnDashboard by remember {
        val raw = settings.getStringOrNull(KEY_CHART_CARDS)?.decodeSet() ?: emptySet()
        // Only keep keys that are real chart card keys — discard any pipe-merged garbage
        val valid = raw.filter { k -> ALL_CHART_CARDS.any { it.key == k } }.toSet()
        mutableStateOf(valid)
    }

    var deletedCards by remember {
        mutableStateOf(
            settings.getStringOrNull(KEY_DELETED_CARDS)?.decodeSet() ?: emptySet()
        )
    }

    val halfPositions = remember {
        mutableStateMapOf<String, Float>().also { positions ->
            positions.putAll(settings.getStringOrNull(KEY_HALF_POSITIONS)?.decodeHalfPositions() ?: emptyMap())
        }
    }

    val cardOrder = remember {
        mutableStateListOf<String>().also { list ->
            val saved = settings.getStringOrNull(KEY_CARD_ORDER)?.decodeOrder()
                ?.filter { k ->
                    // Only keep clean single-key slots (no pipe), and only chart keys
                    // that are actually on the dashboard
                    !k.contains('|') &&
                            (ALL_CHART_CARDS.none { it.key == k } || k in
                                    (settings.getStringOrNull(KEY_CHART_CARDS)?.decodeSet()
                                        ?.filter { ck -> ALL_CHART_CARDS.any { it.key == ck } }?.toSet()
                                        ?: emptySet<String>()))
                }
            list.addAll(saved ?: DEFAULT_CARD_ORDER.decodeOrder())
        }
    }

    fun halfCardsShareRow(left: String?, right: String?): Boolean =
        left != null && right != null && isHalfCardKey(left) && isHalfCardKey(right) &&
                ((halfPositions[left] == null && halfPositions[right] == null) ||
                        (halfPositions[left] == 0f && halfPositions[right] == 1f))

    // Write current layout to settings
    fun persistLayout() {
        settings[KEY_CARD_ORDER]    = cardOrder.encodeOrder()
        settings[KEY_DELETED_CARDS] = deletedCards.encodeSet()
        settings[KEY_CHART_CARDS]   = chartCardsOnDashboard.encodeSet()
        settings[KEY_HALF_POSITIONS] = halfPositions.encodeHalfPositions()
    }

    // Add a chart card — each card always gets its own independent slot
    fun addChartCard(key: String) {
        if (ALL_CHART_CARDS.none { it.key == key }) return
        if (key in chartCardsOnDashboard) return        // already on dashboard
        chartCardsOnDashboard = chartCardsOnDashboard + key
        cardOrder.add(key)
        // No saved lane means this card can pair with the next re-added half
        // card. Explicitly positioned lone cards retain their own lane instead.
        halfPositions.remove(key)
        // Layout is persisted only when the user clicks Done (fix #7)
    }

    // Delete a card — chart cards return to sheet; built-in cards go to deletedCards
    fun deleteCard(key: String) {
        val isChart = ALL_CHART_CARDS.any { it.key == key }
        if (isChart) {
            val index = cardOrder.indexOf(key)
            val leftNeighbour = cardOrder.getOrNull(index - 1)
            val rightNeighbour = cardOrder.getOrNull(index + 1)

            // Only the surviving card in this row is changed.  In particular,
            // do not let the next half-card pair move up into this row.
            if (halfCardsShareRow(leftNeighbour, key)) {
                halfPositions[leftNeighbour!!] = 0f
            } else if (halfCardsShareRow(key, rightNeighbour)) {
                halfPositions[rightNeighbour!!] = 1f
            }
            halfPositions.remove(key)
            chartCardsOnDashboard = chartCardsOnDashboard - key
            cardOrder.remove(key)           // remove by value — always unique
        } else {
            deletedCards = deletedCards + key
        }
        // Layout is persisted only when the user clicks Done (fix #7)
    }

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
                addChartCard(key)
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
                        onClick = {
                            if (isCustomizing) {
                                // Done — persist the current layout
                                persistLayout()
                                layoutSnapshot = null
                                isCustomizing = false
                            } else {
                                // Entering customize — take a snapshot for Cancel
                                layoutSnapshot = LayoutSnapshot(
                                    cardOrder = cardOrder.toList(),
                                    deletedCards = deletedCards,
                                    chartCardsOnDashboard = chartCardsOnDashboard,
                                    halfPositions = halfPositions.toMap()
                                )
                                isCustomizing = true
                            }
                        },
                        onCancel = {
                            // Restore layout from snapshot and discard changes
                            val snap = layoutSnapshot
                            if (snap != null) {
                                cardOrder.clear()
                                cardOrder.addAll(snap.cardOrder)
                                deletedCards = snap.deletedCards
                                chartCardsOnDashboard = snap.chartCardsOnDashboard
                                halfPositions.clear()
                                halfPositions.putAll(snap.halfPositions)
                            }
                            layoutSnapshot = null
                            isCustomizing = false
                        }
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
        // Every entry in cardOrder is a single unique key string.
        // Visible half-size cards that are adjacent get rendered side-by-side.
        // Deletion always uses cardOrder.remove(key) — no index capture.

        // Compute which keys are currently visible
        val visibleKeys = cardOrder.filter { key ->
            val isChart = ALL_CHART_CARDS.any { it.key == key }
            when {
                key in deletedCards -> false
                isChart -> key in chartCardsOnDashboard
                else -> true
            }
        }

        // Group into visual rows: pairs of consecutive half-size cards share a Row
        fun isHalfKey(key: String): Boolean = isHalfCardKey(key)

        val visualRows = mutableListOf<List<String>>()
        var i = 0
        while (i < visibleKeys.size) {
            val key = visibleKeys[i]
            val nextKey = visibleKeys.getOrNull(i + 1)
            val mayShareRow = halfCardsShareRow(key, nextKey)
            if (mayShareRow) {
                visualRows.add(listOf(key, visibleKeys[i + 1]))
                i += 2
            } else {
                visualRows.add(listOf(key))
                i += 1
            }
        }

        // ── Row-aware move helpers ────────────────────────────────────────────
        // These replace the old per-card cardOrder.move(idx, idx±1) calls.
        // They operate on visual rows so that:
        //   • A full card swapping with a paired half-row moves BOTH halves together.
        //   • A full card swapping with another full card only touches those two.
        //   • A lone half card moving into a lone-half row merges them into a pair.

        fun moveRowUp(rowIndex: Int) {
            if (rowIndex <= 0) return
            val thisRow = visualRows[rowIndex]
            val prevRow = visualRows[rowIndex - 1]

            // Case: full card moves up into a paired-half row → both halves shift down
            if (thisRow.size == 1 && !isHalfKey(thisRow[0]) && prevRow.size == 2) {
                val fullIdx   = cardOrder.indexOf(thisRow[0])
                val firstHalfIdx = cardOrder.indexOf(prevRow[0])
                cardOrder.move(fullIdx, firstHalfIdx)
                return
            }

            // Case: full card moves up into another full-card row → simple swap
            if (thisRow.size == 1 && !isHalfKey(thisRow[0]) &&
                prevRow.size == 1 && !isHalfKey(prevRow[0])) {
                val a = cardOrder.indexOf(thisRow[0])
                val b = cardOrder.indexOf(prevRow[0])
                cardOrder.move(a, b)
                return
            }

            // Case: lone half moving up into a lone-half row → merge into a pair
            if (thisRow.size == 1 && isHalfKey(thisRow[0]) &&
                prevRow.size == 1 && isHalfKey(prevRow[0])) {
                val movingKey = thisRow[0]
                val targetKey = prevRow[0]
                halfPositions.remove(movingKey)
                halfPositions.remove(targetKey)
                val fromIdx = cardOrder.indexOf(movingKey)
                val toIdx   = cardOrder.indexOf(targetKey) + 1
                if (fromIdx != toIdx) cardOrder.move(fromIdx, toIdx.coerceAtMost(cardOrder.lastIndex))
                return
            }

            // Default: move the first card of this row up by one slot in cardOrder
            val firstKey = thisRow.first()
            val idx = cardOrder.indexOf(firstKey)
            if (idx > 0) cardOrder.move(idx, idx - 1)
            // Layout is persisted only when the user clicks Done (fix #7)
        }

        fun moveRowDown(rowIndex: Int) {
            if (rowIndex >= visualRows.lastIndex) return
            val thisRow = visualRows[rowIndex]
            val nextRow = visualRows[rowIndex + 1]

            // Case: full card moves down into a paired-half row → both halves shift up
            if (thisRow.size == 1 && !isHalfKey(thisRow[0]) && nextRow.size == 2) {
                val fullIdx      = cardOrder.indexOf(thisRow[0])
                val lastHalfIdx  = cardOrder.indexOf(nextRow.last())
                cardOrder.move(fullIdx, lastHalfIdx)
                return
            }

            // Case: full card moves down into another full-card row → simple swap
            if (thisRow.size == 1 && !isHalfKey(thisRow[0]) &&
                nextRow.size == 1 && !isHalfKey(nextRow[0])) {
                val a = cardOrder.indexOf(thisRow[0])
                val b = cardOrder.indexOf(nextRow[0])
                cardOrder.move(a, b)
                return
            }

            // Case: lone half moving down into a lone-half row → merge into a pair
            if (thisRow.size == 1 && isHalfKey(thisRow[0]) &&
                nextRow.size == 1 && isHalfKey(nextRow[0])) {
                val movingKey = thisRow[0]
                val targetKey = nextRow[0]
                halfPositions.remove(movingKey)
                halfPositions.remove(targetKey)
                val fromIdx = cardOrder.indexOf(movingKey)
                val toIdx   = cardOrder.indexOf(targetKey) + 1
                if (fromIdx != toIdx) cardOrder.move(fromIdx, toIdx.coerceAtMost(cardOrder.lastIndex))
                return
            }

            // Default: move the last card of this row down by one slot in cardOrder
            val lastKey = thisRow.last()
            val idx = cardOrder.indexOf(lastKey)
            if (idx < cardOrder.lastIndex) cardOrder.move(idx, idx + 1)
            // Layout is persisted only when the user clicks Done (fix #7)
        }

        items(visualRows, key = { row -> row.joinToString("|") }) { row ->
            val rowIndex = visualRows.indexOf(row)
            if (row.size == 2) {
                // Side-by-side half-size pair
                Row(
                    modifier = Modifier.fillMaxWidth().height(HALF_CARD_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        val isChart = ALL_CHART_CARDS.any { it.key == key }
                        CustomizableCard(
                            cardKey       = key,
                            isCustomizing = isCustomizing,
                            onDelete      = { deleteCard(key) },
                            onMoveUp      = { moveRowUp(rowIndex) },
                            onMoveDown    = { moveRowDown(rowIndex) },
                            onMoveHorizontally = { delta ->
                                val isLeft = row.first() == key
                                // Dragging either member toward the centre breaks the
                                // pair: the dragged card owns this row and its former
                                // neighbour is squeezed into the following row.
                                if ((isLeft && delta > 0f) || (!isLeft && delta < 0f)) {
                                    val other = row.first { it != key }
                                    halfPositions[key] = 0.5f
                                    halfPositions[other] = 0f
                                    if (!isLeft) {
                                        val from = cardOrder.indexOf(key)
                                        val to = cardOrder.indexOf(other)
                                        cardOrder.move(from, to)
                                    }
                                    // Layout is persisted only when the user clicks Done (fix #7)
                                }
                            },
                            modifier      = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (isChart) ChartCardContent(key, state, filteredRawTransactions)
                            else HalfCardContent(key, state)
                        }
                    }
                }
            } else {
                val key = row[0]
                val def = ALL_CHART_CARDS.find { it.key == key }
                val isChart = def != null

                if (isHalfKey(key)) {
                    // Lone half-size card — occupies left half, spacer on right
                    Row(
                        modifier = Modifier.fillMaxWidth().height(HALF_CARD_HEIGHT),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CustomizableCard(
                            cardKey       = key,
                            isCustomizing = isCustomizing,
                            onDelete      = { deleteCard(key) },
                            onMoveUp      = { moveRowUp(rowIndex) },
                            onMoveDown    = { moveRowDown(rowIndex) },
                            onMoveHorizontally = { delta ->
                                halfPositions[key] = ((halfPositions[key] ?: 0f) + delta).coerceIn(0f, 1f)
                                // Layout is persisted only when the user clicks Done (fix #7)
                            },
                            horizontalPosition = halfPositions[key] ?: 0f,
                            modifier      = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (isChart) ChartCardContent(key, state, filteredRawTransactions)
                            else HalfCardContent(key, state)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    // Full-size card
                    CustomizableCard(
                        cardKey       = key,
                        isCustomizing = isCustomizing,
                        onDelete      = { deleteCard(key) },
                        onMoveUp      = { moveRowUp(rowIndex) },
                        onMoveDown    = { moveRowDown(rowIndex) },
                        modifier      = Modifier.height(FULL_CARD_HEIGHT)
                    ) {
                        when (key) {
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
                            else -> ChartCardContent(key, state, filteredRawTransactions)
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
                        state.recentTransactions.take(6).forEach { tx -> TransactionRow(tx) }
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
    // Desktop card order for newly added chart cards (not persisted — desktop is session-only)
    val desktopChartOrder = remember { mutableStateListOf<String>() }

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

    // Row grouping for desktop chart cards — must live here in Composable scope, not inside LazyListScope
    val desktopChartRows by remember(desktopChartOrder, chartCardsOnDashboard) {
        derivedStateOf {
            val visible = desktopChartOrder.filter { it in chartCardsOnDashboard }
            val rows = mutableListOf<List<String>>()
            var di = 0
            while (di < visible.size) {
                val key     = visible[di]
                val def     = ALL_CHART_CARDS.find { it.key == key }
                val nextKey = visible.getOrNull(di + 1)
                val nextDef = nextKey?.let { k -> ALL_CHART_CARDS.find { it.key == k } }
                if (def?.size == CardSize.HALF && nextDef?.size == CardSize.HALF) {
                    rows.add(listOf(key, nextKey!!))
                    di += 2
                } else {
                    rows.add(listOf(key))
                    di += 1
                }
            }
            rows.toList()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    if (showChartsSheet) {
        ChartsBottomSheet(
            chartCardsOnDashboard = chartCardsOnDashboard,
            onAddChart = { key ->
                if (key !in chartCardsOnDashboard) desktopChartOrder.add(key)
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
                    // Desktop customize snapshot for Cancel support
                    var desktopDeletedSnapshot by remember { mutableStateOf<Set<String>?>(null) }
                    var desktopChartSnapshot   by remember { mutableStateOf<Set<String>?>(null) }
                    var desktopOrderSnapshot   by remember { mutableStateOf<List<String>?>(null) }
                    DashboardCustomizeButton(
                        isCustomizing = isCustomizing,
                        onClick = {
                            if (isCustomizing) {
                                // Done — desktop layout is session-only, nothing to persist
                                desktopDeletedSnapshot = null
                                desktopChartSnapshot   = null
                                desktopOrderSnapshot   = null
                                isCustomizing = false
                            } else {
                                desktopDeletedSnapshot = deletedCards
                                desktopChartSnapshot   = chartCardsOnDashboard
                                desktopOrderSnapshot   = desktopChartOrder.toList()
                                isCustomizing = true
                            }
                        },
                        onCancel = {
                            desktopDeletedSnapshot?.let { deletedCards = it }
                            desktopChartSnapshot?.let { chartCardsOnDashboard = it }
                            desktopOrderSnapshot?.let {
                                desktopChartOrder.clear()
                                desktopChartOrder.addAll(it)
                            }
                            desktopDeletedSnapshot = null
                            desktopChartSnapshot   = null
                            desktopOrderSnapshot   = null
                            isCustomizing = false
                        }
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
                modifier = Modifier.fillMaxWidth().height(300.dp),
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
                modifier = Modifier.fillMaxWidth().height(300.dp),
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

        // ── Dynamically added chart cards (desktop) — rendered above the + Charts button ──
        // desktopChartRows is computed above in Composable scope via derivedStateOf.
        items(desktopChartRows, key = { row -> "drow_${row.joinToString("|")}" }) { row ->
            if (row.size == 2) {
                // Two half-size cards side by side
                Row(
                    modifier = Modifier.fillMaxWidth().height(HALF_CARD_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { key ->
                        CustomizableCard(
                            cardKey = key,
                            isCustomizing = isCustomizing,
                            onDelete = {
                                chartCardsOnDashboard = chartCardsOnDashboard - key
                                desktopChartOrder.remove(key)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            ChartCardContent(key, state, filteredRawTransactions)
                        }
                    }
                }
            } else {
                val key = row[0]
                val def = ALL_CHART_CARDS.find { it.key == key }
                if (def?.size == CardSize.HALF) {
                    // Lone half-size card — renders at half width with a spacer on the right
                    Row(
                        modifier = Modifier.fillMaxWidth().height(HALF_CARD_HEIGHT),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CustomizableCard(
                            cardKey = key,
                            isCustomizing = isCustomizing,
                            onDelete = {
                                chartCardsOnDashboard = chartCardsOnDashboard - key
                                desktopChartOrder.remove(key)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            ChartCardContent(key, state, filteredRawTransactions)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    // Full-size card — spans the full width
                    CustomizableCard(
                        cardKey = key,
                        isCustomizing = isCustomizing,
                        onDelete = {
                            chartCardsOnDashboard = chartCardsOnDashboard - key
                            desktopChartOrder.remove(key)
                        },
                        modifier = Modifier.fillMaxWidth().height(FULL_CARD_HEIGHT)
                    ) {
                        ChartCardContent(key, state, filteredRawTransactions)
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
                modifier = Modifier.fillMaxWidth().height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if ("transactions" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "transactions",
                        isCustomizing = false, // Recent Transactions is not deletable on desktop
                        onDelete = {},
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
                                state.recentTransactions.take(6).forEach { tx -> TransactionRow(tx) }
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
    onMoveHorizontally: (Float) -> Unit = {},
    horizontalPosition: Float? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    // A smaller threshold makes a complete, full-width card practical to move
    // within a single long-press drag.
    val swapThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val trackOffset = if (horizontalPosition == null) 0.dp
        else (maxWidth + 12.dp) * horizontalPosition.coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxSize().offset(x = trackOffset)) {
            DashboardCard(
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
                    .then(if (isCustomizing) Modifier.blur(3.dp) else Modifier),
                content = content
            )

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

                // ── Move handle: centred icon, long-press + drag to reorder ──
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), CircleShape)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart  = { dragAccumY = 0f; dragAccumX = 0f },
                                onDrag       = { change, dragAmount ->
                                    change.consume()
                                    dragAccumX += dragAmount.x
                                    dragAccumY += dragAmount.y
                                    if (kotlin.math.abs(dragAccumX) > kotlin.math.abs(dragAccumY)) {
                                        // Continuous horizontal movement for lone half-card tracks.
                                        onMoveHorizontally(dragAmount.x / 300f)
                                    }
                                    when {
                                        dragAccumY >  swapThresholdPx -> { onMoveDown(); dragAccumY = 0f }
                                        dragAccumY < -swapThresholdPx -> { onMoveUp();   dragAccumY = 0f }
                                    }
                                },
                                onDragEnd    = { dragAccumY = 0f; dragAccumX = 0f },
                                onDragCancel = { dragAccumY = 0f; dragAccumX = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MoveIcon(modifier = Modifier.size(50.dp), color = MaterialTheme.colorScheme.onSurface)
                }
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
    Icon(
        painter = painterResource(Res.drawable.drag_pan),
        contentDescription = "Move",
        tint = color,
        modifier = modifier
    )
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
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
    onClick: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    if (isCustomizing) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Done button — sits where Customise was
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
            // Cancel button — appears to the right of Done
            if (onCancel != null) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Monthly Trend")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(color = Color(0xFF16A34A), label = "In")
                    LegendDot(color = Color(0xFFEF4444), label = "Out")
                }
                LineChart(data = state.monthlyTrend, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
        "top_categories" -> {
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Highest Spending")
                BarChart(data = state.monthlyTopCategories, modifier = Modifier.fillMaxWidth().weight(1f))
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
                    .sumOf { kotlin.math.abs(it.amount) }.toFloat()
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
                    Text("Weekly Spending", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    BarChart(data = weeklyData, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
            }
        }

        // ── Bank Account Comparison (half) ──
        // 2. Scrollable inside the card so no info is cut
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
            val maxAmt = accountSpend.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1f
            val barColors = listOf(Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEC4899))
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bank Comparison", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                                    text = formatCurrency(amount.toDouble(), sym),
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
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Time of Day", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    DonutChart(categories = cats, modifier = Modifier.size(80.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cats.forEach { cat ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(7.dp).background(cat.color, CircleShape))
                                Text(
                                    text = "${cat.name} ${(cat.percent * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    maxLines = 1
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
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .groupBy { tx -> tx.merchantName?.ifBlank { null } ?: tx.description }
                .map { (name, txList) -> name to txList.sumOf { kotlin.math.abs(it.amount) } }
                .sortedByDescending { it.second }
                .take(5)
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Largest Transactions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (top5.isEmpty()) {
                    Text("No data", style = MaterialTheme.typography.bodySmall,
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
                                    text = formatCurrency(amount, sym),
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
                .filter { tx ->
                    val p = tx.timestamp.take(10).split("-")
                    p.size == 3 &&
                            p[0].toIntOrNull() == now.year &&
                            p[1].toIntOrNull() == now.month.number &&
                            tx.amount < 0
                }
                .groupBy { tx -> tx.merchantName?.ifBlank { null } ?: tx.description }
                .map { (name, txList) -> name to txList.sumOf { kotlin.math.abs(it.amount) } }
                .sortedBy { it.second }
                .take(5)
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Smallest Transactions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (bottom5.isEmpty()) {
                    Text("No data", style = MaterialTheme.typography.bodySmall,
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
                                    text = formatCurrency(amount, sym),
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

        "upcoming_bills" -> {
            val bills = remember(rawTransactions) { inferUpcomingBills(rawTransactions) }
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Upcoming Bills", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text("Predicted from history", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bills.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recurring payments identified yet", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    bills.forEach { bill ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.merchant, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${bill.cadence} · expected ${bill.expectedDate}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatCurrency(bill.amount, sym), style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
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
                    val total = txList.sumOf { kotlin.math.abs(it.amount) }
                    name to Triple(count, total / count, total)
                }
                .sortedByDescending { it.second.third }
                .mapIndexed { i, (name, triple) ->
                    MerchantBubble(name, triple.first, triple.second, triple.third, bubbleColors[i % bubbleColors.size])
                }

            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("Merchant Spending Treemap", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("area = total spend",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (bubbles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No data this month", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val maxVisit = bubbles.maxOf { it.visitCount }.toFloat().coerceAtLeast(1f)
                    val maxAvg   = bubbles.maxOf { it.avgAmount  }.toFloat().coerceAtLeast(1f)
                    val maxTotal = bubbles.maxOf { it.totalSpend }.toFloat().coerceAtLeast(1f)

                    // Axis hint row
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("↑ visits", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("avg spend →", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Everything drawn on a single Canvas — nothing clips
                    val textMeasurer = rememberTextMeasurer()
                    Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val w = size.width
                        val h = size.height

                        // Max bubble radius = 20% of the shorter axis, min = 8% — so even the
                        // smallest bubble is readable and the largest never overflows.
                        val maxR = minOf(w, h) * 0.20f
                        val minR = minOf(w, h) * 0.08f

                        // Grid lines
                        val gridColor = Color(0x22888888)
                        // Conventional bottom X-axis: the average-spend scale and
                        // its ticks live below the plotted bubbles, not at the top.
                        val xAxisY = h - 12.dp.toPx()
                        drawLine(gridColor, Offset(0f, xAxisY), Offset(w, xAxisY), 1.dp.toPx())
                        listOf(0f, 0.33f, 0.66f, 1f).forEach { fraction ->
                            val x = w * fraction
                            drawLine(gridColor, Offset(x, xAxisY), Offset(x, xAxisY + 4.dp.toPx()), 1.dp.toPx())
                        }
                        listOf(0.33f, 0.66f).forEach { frac ->
                            drawLine(gridColor, Offset(0f, h * frac), Offset(w, h * frac), 1.dp.toPx())
                            drawLine(gridColor, Offset(w * frac, 0f), Offset(w * frac, h), 1.dp.toPx())
                        }

                        val totalTreemapSpend = bubbles.sumOf { it.totalSpend }.toFloat().coerceAtLeast(1f)
                        var treemapX = 0f
                        bubbles.forEach { b ->
                            // X: avg amount (low left → high right), margin = maxR so bubble never clips edge
                            val xFrac = (b.avgAmount.toFloat() / maxAvg).coerceIn(0f, 1f)
                            val yFrac = 1f - (b.visitCount.toFloat() / maxVisit).coerceIn(0f, 1f)

                            // Each contiguous tile's width is its share of the
                            // month's spending, forming a full-card treemap.
                            val tileWidth = if (b == bubbles.last()) w - treemapX
                            else w * (b.totalSpend.toFloat() / totalTreemapSpend)
                            val tilePadding = 2.dp.toPx()
                            val tileHeight = h * 0.72f
                            val tileTop = (h - tileHeight) / 2f
                            drawRoundRect(
                                color = b.color.copy(alpha = 0.84f),
                                topLeft = Offset(treemapX + tilePadding, tileTop + tilePadding),
                                size = Size((tileWidth - 2 * tilePadding).coerceAtLeast(1f), (tileHeight - 2 * tilePadding).coerceAtLeast(1f)),
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )
                            val r = minOf(tileWidth, tileHeight) / 2f
                            val cx = treemapX + tileWidth / 2f
                            val cy = h / 2f
                            treemapX += tileWidth

                            // Merchant name — fits inside the circle
                            val nameStyle = TextStyle(
                                fontSize = (r * 0.28f / density).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            val countStyle = TextStyle(
                                fontSize = (r * 0.22f / density).sp,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )

                            val nameLayout  = textMeasurer.measure(b.name.take(8), nameStyle,
                                constraints = Constraints(maxWidth = (r * 1.6f).toInt()))
                            val countLayout = textMeasurer.measure("${b.visitCount}×", countStyle)

                            val totalTextH = nameLayout.size.height + countLayout.size.height + 2.dp.toPx()

                            drawText(nameLayout,  topLeft = Offset(cx - nameLayout.size.width  / 2f, cy - totalTextH / 2f))
                            drawText(countLayout, topLeft = Offset(cx - countLayout.size.width / 2f,
                                cy - totalTextH / 2f + nameLayout.size.height + 2.dp.toPx()))
                        }
                    }
                    val totalSpend = bubbles.sumOf { it.totalSpend }.coerceAtLeast(1.0)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(148.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bubbles.forEach { merchant ->
                            val tileWidth = (96f + 180f * (merchant.totalSpend / totalSpend).toFloat()).dp
                            Box(
                                modifier = Modifier.width(tileWidth).height(132.dp)
                                    .background(merchant.color.copy(alpha = 0.84f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(merchant.name, color = Color.White, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    Text(formatCurrency(merchant.totalSpend, sym), color = Color.White.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
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