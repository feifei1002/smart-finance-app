package com.smart_finance_app.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import kotlinx.coroutines.launch
import com.smart_finance_app.budget.BudgetApi
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.arrow_drop_down
import smart_finance_app.shared.generated.resources.bank
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.CurrencyController
import com.smart_finance_app.currency.ExchangeRateService
import com.smart_finance_app.currency.getCurrencySymbol


/** Fixed height for every half-size card (side-by-side pair). */
private val HALF_CARD_HEIGHT = 200.dp

/** Fixed height for every full-size card. */
private val FULL_CARD_HEIGHT = 240.dp

/** Treemap gets a shorter fixed height — the scroll handles detail visibility. */
private val TREEMAP_CARD_HEIGHT = 180.dp

/** Swaps two elements in a MutableList by index. */
private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

@Composable
fun DashboardScreen(
    authToken: String,
    userId: String,
    userName: String,
    apiBaseUrl: String,
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

    // Add rates state near other state declarations (line 232)
    var exchangeRates by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var ratesWarning by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isLoading = true
        errorMsg  = null

        val rates = ExchangeRateService.getRates(api.client, apiBaseUrl)
        exchangeRates = rates

        if (rates.isEmpty() && CurrencyController.currentCurrency != "GBP") {
            ratesWarning = AppStrings.get(
                LocaleController.currentLanguageCode,
                StringKey.CURRENCY_RATES_UNAVAILABLE
            )
        } else {
            ratesWarning = null
        }

        val a = api.getAccounts(authToken)
        if (a is DashboardResult.Failure) {
            errorMsg = AppStrings.get(LocaleController.currentLanguageCode, a.message)
            isLoading = false
            return
        }
        val accounts = (a as DashboardResult.Success).data

        if (accounts.isEmpty()) {
            state     = null
            isLoading = false
            return
        }

        val b = api.getBalances(authToken)
        if (b is DashboardResult.Failure) {
            errorMsg = AppStrings.get(LocaleController.currentLanguageCode, b.message)
            isLoading = false
            return
        }
        val balances = (b as DashboardResult.Success).data

        state = computeDashboardState(
            balances        = balances,
            transactions    = transactions,
            accounts        = accounts,
            displayCurrency = CurrencyController.currentCurrency,
            rates           = rates
        )
        isLoading = false
    }

// Also recompute when currency changes
    LaunchedEffect(authToken, transactions, CurrencyController.currentCurrency) { load() }


    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(
                            appStringResource(StringKey.DASHBOARD_LOADING),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)) {
                        Text(appStringResource(StringKey.DASHBOARD_ERROR_TITLE),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        errorMsg?.let {
                            AppErrorMessage(it)
                        }
                        Button(onClick = { scope.launch { load() } }) {
                            Text(
                                text = appStringResource(StringKey.DASHBOARD_RETRY),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                        Text(appStringResource(StringKey.DASHBOARD_NO_ACCOUNTS_TITLE),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(appStringResource(StringKey.DASHBOARD_NO_ACCOUNTS_SUBTITLE),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { onConnectAccountClicked() },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = appStringResource(StringKey.DASHBOARD_CONNECT_ACCOUNT),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                        userId = userId,
                        api = api,
                        authToken = authToken,
                        spendingPeriod = spendingPeriod,
                        selectedAccounts = selectedAccounts,
                        onAccountsChanged = { selectedAccounts = it },
                        onPeriodSelected = { spendingPeriod = it },
                        onViewAllTransactionsClicked = onViewAllTransactionsClicked,
                        budgetApi = budgetApi,
                        exchangeRates = exchangeRates,
                        ratesWarning  = ratesWarning
                    )
                } else {
                    DesktopDashboard(
                        state = state!!,
                        userName = userName,
                        userId = userId,
                        api = api,
                        authToken = authToken,
                        spendingPeriod = spendingPeriod,
                        selectedAccounts = selectedAccounts,
                        onAccountsChanged = { selectedAccounts = it },
                        onPeriodSelected = { spendingPeriod = it },
                        onViewAllTransactionsClicked = onViewAllTransactionsClicked,
                        budgetApi = budgetApi,
                        exchangeRates = exchangeRates,
                        ratesWarning  = ratesWarning
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
    userId: String,
    api: DashboardApi,
    budgetApi: BudgetApi,
    authToken: String,
    spendingPeriod: SpendingPeriod,
    selectedAccounts: Set<String>,
    onAccountsChanged: (Set<String>) -> Unit,
    onPeriodSelected: (SpendingPeriod) -> Unit,
    onViewAllTransactionsClicked: () -> Unit,
    exchangeRates: Map<String, Double>,
    ratesWarning: String? = null,
) {
    val greeting = rememberGreeting()
    val scope    = rememberCoroutineScope()
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
    // Keys are prefixed with userId so each user gets their own layout on shared devices.
    val settings = remember { Settings() }
    val keyCardOrder     = remember(userId) { "${userId}_${KEY_CARD_ORDER}" }
    val keyDeletedCards  = remember(userId) { "${userId}_${KEY_DELETED_CARDS}" }
    val keyChartCards    = remember(userId) { "${userId}_${KEY_CHART_CARDS}" }
    val keyHalfPositions = remember(userId) { "${userId}_${KEY_HALF_POSITIONS}" }
    val keyMigrated      = remember(userId) { "${userId}_${KEY_MIGRATED}" }

    // One-time migration: wipe any v1 paired-slot data so we start clean
    remember(userId) {
        if (!settings.getBoolean(keyMigrated, false)) {
            settings.remove("card_order")
            settings.remove("deleted_cards")
            settings.remove("chart_cards_on_dashboard")
            // Also wipe v2 keys so state is fully fresh — all 6 charts go back to sheet
            settings.remove(keyCardOrder)
            settings.remove(keyDeletedCards)
            settings.remove(keyChartCards)
            settings.putBoolean(keyMigrated, true)
        }
    }

    var chartCardsOnDashboard by remember(userId) {
        val raw = settings.getStringOrNull(keyChartCards)?.decodeSet() ?: emptySet()
        // Only keep keys that are real chart card keys — discard any pipe-merged garbage
        val valid = raw.filter { k -> isActiveChartCardKey(k) }.toSet()
        mutableStateOf(valid)
    }

    var deletedCards by remember(userId) {
        mutableStateOf(
            (settings.getStringOrNull(keyDeletedCards)?.decodeSet() ?: emptySet()) - "budget"
        )
    }

    val halfPositions = remember(userId) {
        mutableStateMapOf<String, Float>().also { positions ->
            positions.putAll(settings.getStringOrNull(keyHalfPositions)?.decodeHalfPositions() ?: emptyMap())
        }
    }

    val cardOrder = remember(userId) {
        mutableStateListOf<String>().also { list ->
            val defaults = DEFAULT_CARD_ORDER
                .decodeOrder()
                .filter { key -> isKnownDashboardCardKey(key) && key != "budget" }
                .distinct()
            val savedCharts = settings.getStringOrNull(keyChartCards)
                ?.decodeSet()
                ?.filter(::isActiveChartCardKey)
                ?.toSet()
                ?: emptySet()

            val saved = settings.getStringOrNull(keyCardOrder)
                ?.decodeOrder()
                ?.filter { k ->
                    !k.contains('|') &&
                            isKnownDashboardCardKey(k) &&
                            (!isActiveChartCardKey(k) || k in savedCharts)
                }

            val base = saved?.ifEmpty { defaults } ?: defaults
            list.addAll(base + defaults.filter { it !in base })
        }
    }

    fun halfCardsShareRow(left: String?, right: String?): Boolean =
        left != null &&
                right != null &&
                isHalfCardKey(left) &&
                isHalfCardKey(right)

    // Write current layout to local settings AND push to the backend so other
    // platforms pick it up. The API call is fire-and-forget with local as fallback.
    fun persistLayout() {
        settings[keyCardOrder]     = cardOrder.encodeOrder()
        settings[keyDeletedCards]  = deletedCards.encodeSet()
        settings[keyChartCards]    = chartCardsOnDashboard.encodeSet()
        settings[keyHalfPositions] = halfPositions.encodeHalfPositions()
        // Push to backend — other platforms will read this on next load
        scope.launch {
            runCatching {
                api.saveLayout(
                    authToken,
                    DashboardLayoutDto(
                        cardOrder     = cardOrder.encodeOrder(),
                        deletedCards  = deletedCards.encodeSet(),
                        chartCards    = chartCardsOnDashboard.encodeSet(),
                        halfPositions = halfPositions.encodeHalfPositions()
                    )
                )
            }
        }
    }

    // Pull layout from backend on entry — keyed on authToken so it fires once the
    // token is actually available (userId alone may fire before token is ready).
    LaunchedEffect(authToken) {
        if (authToken.isBlank()) return@LaunchedEffect
        runCatching { api.loadLayout(authToken) }.getOrNull()?.let { remote ->
            if (remote.chartCards.isNotBlank() || remote.deletedCards.isNotBlank()
                || remote.cardOrder.isNotBlank() && remote.cardOrder != DEFAULT_CARD_ORDER) {
                val remoteDeleted = remote.deletedCards.decodeSet() - "budget"
                val remotePos     = remote.halfPositions.decodeHalfPositions()

                val defaults = DEFAULT_CARD_ORDER
                    .decodeOrder()
                    .filter { key -> isKnownDashboardCardKey(key) && key != "budget" }
                    .distinct()
                val savedRemoteOrder = remote.cardOrder.decodeOrder()
                    .filter { k -> !k.contains('|') && isKnownDashboardCardKey(k) }

                val baseRemoteOrder = savedRemoteOrder.ifEmpty { defaults }
                val remoteOrder = baseRemoteOrder + defaults.filter { it !in baseRemoteOrder }

                val chartsInOrder = remote.chartCards.decodeSet()
                    .filter { k -> isActiveChartCardKey(k) && k in remoteOrder }
                    .toSet()

                cardOrder.clear(); cardOrder.addAll(remoteOrder)
                deletedCards          = remoteDeleted
                chartCardsOnDashboard = chartsInOrder   // only charts actually in order
                halfPositions.clear(); halfPositions.putAll(remotePos)

                settings[keyCardOrder]     = cardOrder.encodeOrder()
                settings[keyDeletedCards]  = deletedCards.encodeSet()
                settings[keyChartCards]    = chartCardsOnDashboard.encodeSet()
                settings[keyHalfPositions] = halfPositions.encodeHalfPositions()
            }
        }
    }

    // Add a chart card — persists immediately so re-login doesn't lose the change,
    // and pushes to backend so other platforms sync right away.
    fun addChartCard(key: String) {
        if (key in BUILT_IN_DASHBOARD_CARD_KEYS) {
            deletedCards = deletedCards - key
            chartCardsOnDashboard = chartCardsOnDashboard - key
            halfPositions.remove(key)

            if (key !in cardOrder) {
                cardOrder.add(key)
            }
        } else {
            if (!isActiveChartCardKey(key)) return
            if (key in chartCardsOnDashboard) return

            chartCardsOnDashboard = chartCardsOnDashboard + key
            cardOrder.add(key)
            halfPositions.remove(key)
        }

        while (cardOrder.count { it == key } > 1) {
            cardOrder.removeAt(cardOrder.lastIndexOf(key))
        }

        if (!isCustomizing) {
            persistLayout()
        }
    }

    // Delete a card — persists immediately for the same reason.
    fun deleteCard(key: String) {
        val isChart = isActiveChartCardKey(key)
        if (isChart) {
            val index = cardOrder.indexOf(key)
            val leftNeighbour = cardOrder.getOrNull(index - 1)
            val rightNeighbour = cardOrder.getOrNull(index + 1)
            if (halfCardsShareRow(leftNeighbour, key)) {
                halfPositions[leftNeighbour!!] = 0f
            } else if (halfCardsShareRow(key, rightNeighbour)) {
                halfPositions[rightNeighbour!!] = 1f
            }
            halfPositions.remove(key)
            chartCardsOnDashboard = chartCardsOnDashboard - key
            cardOrder.remove(key)
        } else {
            deletedCards = deletedCards + key
        }
        if (!isCustomizing) {
            persistLayout()
        }
    }

    // ── 1. FILTERED BALANCES & ACCOUNTS ──
    val activeAccounts = remember(selectedAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.accounts
        else state.accounts.filter { it.bankName in selectedAccounts }
    }

    // Calculates display balance based on selected account(s)
    val displayBalance = remember(activeAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.currentBalance
        else state.accounts
            .filter { it.bankName in selectedAccounts }
            .sumOf { it.balanceValue ?: 0.0 }
    }

    // ── 2. FILTERED RAW TRANSACTIONS ──
    val selectedAccountIds = remember(selectedAccounts, state.accounts) {
        state.accounts
            .filter { it.bankName in selectedAccounts }
            .map { it.accountId }
            .toSet()
    }

    val filteredRawTransactions = remember(selectedAccountIds, state.rawTransactions) {
        if (selectedAccountIds.isEmpty()) state.rawTransactions
        else state.rawTransactions.filter { tx ->
//            state.accounts.find { it.bankName in selectedAccounts } != null
            tx.accountId in selectedAccountIds
        }
    }

    val (displayMonthlyIncome, displayMonthlyExpenses) = remember(
        filteredRawTransactions,
        exchangeRates,
        CurrencyController.currentCurrency
    ) {
        currentMonthIncomeAndExpenses(
            transactions = filteredRawTransactions,
            displayCurrency = CurrencyController.currentCurrency,
            rates = exchangeRates
        )
    }

    // Label on the top-right button
    val allAccountsLabel = appStringResource(StringKey.DASHBOARD_ALL_ACCOUNTS)
    val accountsCountLabel = appStringResource(StringKey.DASHBOARD_ACCOUNTS_COUNT)
    val selectorLabel = if (selectedAccounts.isEmpty()) allAccountsLabel
    else if (selectedAccounts.size == 1) selectedAccounts.first()
    else accountsCountLabel.replace("%1\$d", "${selectedAccounts.size}")

    @OptIn(ExperimentalMaterial3Api::class)
    if (showChartsSheet) {
        ChartsBottomSheet(
            chartCardsOnDashboard = chartCardsOnDashboard,
            deletedCards = deletedCards,
            onAddChart = { key ->
                addChartCard(key)
                showChartsSheet = false
            },
            onDismiss = { showChartsSheet = false }
        )
    }

    // Locked to true only while the user is actively dragging a move handle —
    // prevents the LazyColumn from scrolling under the finger during a card drag.
    var isDraggingHandle by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp),
        userScrollEnabled = !isDraggingHandle
    ) {
        ratesWarning?.let { warning ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
        // Header block: greeting, subtitle, and controls all tightly grouped
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$greeting, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(appStringResource(StringKey.DASHBOARD_SUBTITLE),
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
                                        Text(allAccountsLabel, style = MaterialTheme.typography.bodySmall)
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
                balance  = formatCurrency(displayBalance,        getCurrencySymbol(state.currency), state.currency),
                balanceTrend = state.balanceChangePercent,
                income   = formatCurrency(displayMonthlyIncome, getCurrencySymbol(state.currency), state.currency),
                incomeTrend = state.incomeChangePercent,
                expenses = formatCurrency(displayMonthlyExpenses, getCurrencySymbol(state.currency), state.currency),
                expensesTrend = state.expenseChangePercent
            )
        }

        // ── Dynamic card list ─────────────────────────────────────────────────
        // Every entry in cardOrder is a single unique key string.
        // Visible half-size cards that are adjacent get rendered side-by-side.
        // Deletion always uses cardOrder.remove(key) — no index capture.

        // Compute which keys are currently visible
        val visibleKeys = cardOrder.distinct().filter { key ->
            when {
                !isKnownDashboardCardKey(key) -> false
                key in deletedCards -> false
                isActiveChartCardKey(key) -> key in chartCardsOnDashboard
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
                        val isChart = isActiveChartCardKey(key)
                        CustomizableCard(
                            cardKey       = key,
                            isCustomizing = isCustomizing,
                            onDelete      = { deleteCard(key) },
                            onMoveUp      = { moveRowUp(rowIndex) },
                            onMoveDown    = { moveRowDown(rowIndex) },
                            onDragStarted = { isDraggingHandle = true },
                            onDragEnded   = { isDraggingHandle = false },
                            onMoveHorizontally = { delta ->
                                val isLeft = row.first() == key
                                if ((isLeft && delta > 0f) || (!isLeft && delta < 0f)) {
                                    val other = row.first { it != key }
                                    halfPositions[key] = 0.5f
                                    halfPositions[other] = 0f
                                    if (!isLeft) {
                                        val from = cardOrder.indexOf(key)
                                        val to = cardOrder.indexOf(other)
                                        cardOrder.move(from, to)
                                    }
                                }
                            },
                            modifier      = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (isChart) ChartCardContent(
                                key             = key,
                                state           = state,
                                rawTransactions = filteredRawTransactions,
                                displayCurrency = CurrencyController.currentCurrency,
                                rates           = exchangeRates
                            )
                            else HalfCardContent(key, state)
                        }
                    }
                }
            } else {
                val key = row[0]
                val isChart = isActiveChartCardKey(key)

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
                            onDragStarted = { isDraggingHandle = true },
                            onDragEnded   = { isDraggingHandle = false },
                            onMoveHorizontally = { delta ->
                                halfPositions[key] = ((halfPositions[key] ?: 0f) + delta).coerceIn(0f, 1f)
                            },
                            horizontalPosition = halfPositions[key] ?: 0f,
                            modifier      = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (isChart) ChartCardContent(
                                key             = key,
                                state           = state,
                                rawTransactions = filteredRawTransactions,
                                displayCurrency = CurrencyController.currentCurrency,
                                rates           = exchangeRates
                            )
                            else HalfCardContent(key, state)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    // Full-size card
                    val cardHeight = if (key == "merchant_frequency") TREEMAP_CARD_HEIGHT else FULL_CARD_HEIGHT
                    CustomizableCard(
                        cardKey       = key,
                        isCustomizing = isCustomizing,
                        onDelete      = { deleteCard(key) },
                        onMoveUp      = { moveRowUp(rowIndex) },
                        onMoveDown    = { moveRowDown(rowIndex) },
                        onDragStarted = { isDraggingHandle = true },
                        onDragEnded   = { isDraggingHandle = false },
                        modifier      = Modifier.height(cardHeight)
                    ) {
                        when (key) {
                            "spending" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SpendingOverviewHeader(selectedPeriod = spendingPeriod, onPeriodSelected = onPeriodSelected)
                                    val filteredCategories = computeSpendingCategories(
                                        transactions    = filteredRawTransactions,
                                        period          = spendingPeriod,
                                        displayCurrency = CurrencyController.currentCurrency,
                                        rates           = exchangeRates
                                    )
                                    val chartCategories = filteredCategories.filter { it.percent >= 0.01f }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DonutChart(categories = chartCategories, modifier = Modifier.size(120.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (filteredCategories.isEmpty()) {
                                                Text("No spending data yet", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                filteredCategories.forEach { cat ->
                                                    CategoryLegendRow(cat)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> ChartCardContent(
                                key             = key,
                                state           = state,
                                rawTransactions = filteredRawTransactions,
                                displayCurrency = CurrencyController.currentCurrency,
                                rates           = exchangeRates
                            )
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

        // Budget Progress — fixed, not deletable
        item {
            DashboardCard(modifier = Modifier.height(FULL_CARD_HEIGHT)) {
                BudgetProgressCardContent(
                    api = budgetApi,
                    authToken = authToken,
                    transactions = state.rawTransactions,
                    currency = state.currency,
                    exchangeRates = exchangeRates
                )
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
                        SectionTitle(appStringResource(StringKey.DASHBOARD_RECENT_TRANSACTIONS))
                        Text(text = appStringResource(StringKey.DASHBOARD_VIEW_ALL),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onViewAllTransactionsClicked() }
                        )
                    }
                    if (state.recentTransactions.isEmpty()) {
                        Text(appStringResource(StringKey.TRANSACTIONS_EMPTY), style = MaterialTheme.typography.bodySmall,
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
    userId: String,
    api: DashboardApi,
    budgetApi: BudgetApi,
    authToken: String,
    spendingPeriod: SpendingPeriod,
    selectedAccounts: Set<String>,
    onAccountsChanged: (Set<String>) -> Unit,
    onPeriodSelected: (SpendingPeriod) -> Unit,
    onViewAllTransactionsClicked: () -> Unit,
    exchangeRates: Map<String, Double>,
    ratesWarning: String? = null,
) {
    val greeting = rememberGreeting()
    val scope    = rememberCoroutineScope()
    val accountOptions = state.accounts.map { it.bankName }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var isCustomizing by remember { mutableStateOf(false) }
    var showChartsSheet by remember { mutableStateOf(false) }

    // Snapshot for Cancel — hoisted here so they survive LazyColumn recomposition
    var desktopDeletedSnapshot by remember { mutableStateOf<Set<String>?>(null) }
    var desktopChartSnapshot   by remember { mutableStateOf<Set<String>?>(null) }
    var desktopOrderSnapshot   by remember { mutableStateOf<List<String>?>(null) }
    var desktopBuiltinSnapshot by remember { mutableStateOf<List<String>?>(null) }

    // Order of the two built-in row groups — user can drag to reorder them
    val desktopBuiltinOrder = remember {
        mutableStateListOf("spending_trend_row", "categories_budget_row")
    }

    // ── Persisted layout state — same Settings keys as MobileDashboard ──────────
    // This means customisations sync between mobile and desktop via shared storage.
    val settings       = remember { Settings() }
    val keyCardOrder     = remember(userId) { "${userId}_${KEY_CARD_ORDER}" }
    val keyDeletedCards  = remember(userId) { "${userId}_${KEY_DELETED_CARDS}" }
    val keyChartCards    = remember(userId) { "${userId}_${KEY_CHART_CARDS}" }
    val keyMigrated      = remember(userId) { "${userId}_${KEY_MIGRATED}" }
    val keyHalfPositions = remember(userId) { "${userId}_${KEY_HALF_POSITIONS}" }

    var deletedCards by remember(userId) {
        mutableStateOf(
            (settings.getStringOrNull(keyDeletedCards)?.decodeSet() ?: emptySet()) - "budget"
        )
    }
    var chartCardsOnDashboard by remember(userId) {
        val raw = settings.getStringOrNull(keyChartCards)?.decodeSet() ?: emptySet()
        val valid = raw.filter { k -> isActiveChartCardKey(k) }.toSet()
        mutableStateOf(valid)
    }

    // Desktop chart order — initialised from persisted chart set, saved on Done
    val desktopChartOrder = remember(userId) {
        val saved = settings.getStringOrNull(keyChartCards)?.decodeSet()
            ?.filter { k -> isActiveChartCardKey(k) } ?: emptyList()
        mutableStateListOf<String>().also { it.addAll(saved) }
    }

    fun persistDesktopLayout() {
        val fullOrder = (
                DEFAULT_CARD_ORDER.decodeOrder() + desktopChartOrder.filter { it in chartCardsOnDashboard }
                )
            .filter { key -> isKnownDashboardCardKey(key) }
            .distinct()

        settings[keyCardOrder] = fullOrder.encodeOrder()
        settings[keyDeletedCards] = deletedCards.encodeSet()
        settings[keyChartCards] = chartCardsOnDashboard.encodeSet()

        val existingHalfPositions = settings.getStringOrNull(keyHalfPositions) ?: ""

        scope.launch {
            runCatching {
                api.saveLayout(
                    authToken,
                    DashboardLayoutDto(
                        cardOrder = fullOrder.encodeOrder(),
                        deletedCards = deletedCards.encodeSet(),
                        chartCards = chartCardsOnDashboard.encodeSet(),
                        halfPositions = existingHalfPositions
                    )
                )
            }
        }
    }

    // Pull layout from backend on entry — keyed on authToken so it fires once the
    // token is actually available (userId alone may fire before token is ready).
    LaunchedEffect(authToken) {
        if (authToken.isBlank()) return@LaunchedEffect
        runCatching { api.loadLayout(authToken) }.getOrNull()?.let { remote ->
            if (remote.chartCards.isNotBlank() || remote.deletedCards.isNotBlank()) {
                val remoteDeleted = remote.deletedCards.decodeSet() - "budget"
                val remoteOrder   = remote.cardOrder.decodeOrder()
                    .filter { k -> k in remote.chartCards.decodeSet() && ALL_CHART_CARDS.any { it.key == k } }
                // Only keep charts that are actually in the saved order
                val chartsInOrder = remoteOrder.toSet()
                deletedCards          = remoteDeleted
                chartCardsOnDashboard = chartsInOrder
                desktopChartOrder.clear()
                desktopChartOrder.addAll(remoteOrder)

                settings[keyCardOrder] = remote.cardOrder
                settings[keyDeletedCards] = remoteDeleted.encodeSet()
                settings[keyChartCards] = chartsInOrder.encodeSet()
                settings[keyHalfPositions] = remote.halfPositions
            }
        }
    }

    // ── 1. FILTERED BALANCES & ACCOUNTS ──
    val activeAccounts = remember(selectedAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.accounts
        else state.accounts.filter { it.bankName in selectedAccounts }
    }

    // Calculates display balance based on selected account(s)
    val displayBalance = remember(activeAccounts, state.accounts) {
        if (selectedAccounts.isEmpty()) state.currentBalance
        else state.accounts
            .filter { it.bankName in selectedAccounts }
            .sumOf { it.balanceValue ?: 0.0 }
    }

    // ── 2. FILTERED RAW TRANSACTIONS ──
    val selectedAccountIds = remember(selectedAccounts, state.accounts) {
        state.accounts
            .filter { it.bankName in selectedAccounts }
            .map { it.accountId }
            .toSet()
    }

    val filteredRawTransactions = remember(selectedAccountIds, state.rawTransactions) {
        if (selectedAccountIds.isEmpty()) state.rawTransactions
        else state.rawTransactions.filter { tx ->
//            state.accounts.find { it.bankName in selectedAccounts } != null
            tx.accountId in selectedAccountIds
        }
    }

    val (displayMonthlyIncome, displayMonthlyExpenses) = remember(
        filteredRawTransactions,
        exchangeRates,
        CurrencyController.currentCurrency
    ) {
        currentMonthIncomeAndExpenses(
            transactions = filteredRawTransactions,
            displayCurrency = CurrencyController.currentCurrency,
            rates = exchangeRates
        )
    }

    val allAccountsLabel = appStringResource(StringKey.DASHBOARD_ALL_ACCOUNTS)
    val accountsCountLabel = appStringResource(StringKey.DASHBOARD_ACCOUNTS_COUNT)
    val selectorLabel = if (selectedAccounts.isEmpty()) allAccountsLabel
    else if (selectedAccounts.size == 1) selectedAccounts.first()
    else accountsCountLabel.replace("%1\$d", "${selectedAccounts.size}")

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
                    rows.add(listOf(key, nextKey))
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
            deletedCards = deletedCards,
            onAddChart = { key ->
                if (key in BUILT_IN_DASHBOARD_CARD_KEYS) {
                    deletedCards = deletedCards - key
                } else {
                    if (key !in chartCardsOnDashboard) desktopChartOrder.add(key)
                    chartCardsOnDashboard = chartCardsOnDashboard + key
                }
                showChartsSheet = false
            },
            onDismiss = { showChartsSheet = false }
        )
    }

    ratesWarning?.let { warning ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
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
                Text(appStringResource(StringKey.DASHBOARD_SUBTITLE),
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
                        onClick = {
                            if (isCustomizing) {
                                persistDesktopLayout()
                                desktopDeletedSnapshot = null
                                desktopChartSnapshot   = null
                                desktopOrderSnapshot   = null
                                desktopBuiltinSnapshot = null
                                isCustomizing = false
                            } else {
                                desktopDeletedSnapshot = deletedCards
                                desktopChartSnapshot   = chartCardsOnDashboard
                                desktopOrderSnapshot   = desktopChartOrder.toList()
                                desktopBuiltinSnapshot = desktopBuiltinOrder.toList()
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
                            desktopBuiltinSnapshot?.let {
                                desktopBuiltinOrder.clear()
                                desktopBuiltinOrder.addAll(it)
                            }
                            desktopDeletedSnapshot = null
                            desktopChartSnapshot   = null
                            desktopOrderSnapshot   = null
                            desktopBuiltinSnapshot = null
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
                                        Text(allAccountsLabel, style = MaterialTheme.typography.bodySmall)
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
                balance  = formatCurrency(displayBalance,        getCurrencySymbol(state.currency), state.currency),
                balanceTrend = state.balanceChangePercent,
                income   = formatCurrency(displayMonthlyIncome, getCurrencySymbol(state.currency), state.currency),
                incomeTrend = state.incomeChangePercent,
                expenses = formatCurrency(displayMonthlyExpenses, getCurrencySymbol(state.currency), state.currency),
                expensesTrend = state.expenseChangePercent
            )
        }

        // ── Built-in cards — order is persisted in desktopBuiltinOrder ──────────
        // Each card is a full LazyColumn item so the move handle can reorder them.
        itemsIndexed(
            desktopBuiltinOrder.filter { it !in deletedCards },
            key = { _, key -> "builtin_$key" }
        ) { builtinIdx, key ->

            fun builtinMoveUp() {
                val idx = desktopBuiltinOrder.indexOf(key)
                if (idx > 0) desktopBuiltinOrder.move(idx, idx - 1)
            }
            fun builtinMoveDown() {
                val idx = desktopBuiltinOrder.indexOf(key)
                if (idx < desktopBuiltinOrder.lastIndex) desktopBuiltinOrder.move(idx, idx + 1)
            }

            when (key) {
                "spending_trend_row" -> Row(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if ("spending" !in deletedCards) {
                        CustomizableCard(
                            cardKey = "spending",
                            isCustomizing = isCustomizing,
                            onDelete = { deletedCards = deletedCards + it },
                            onMoveUp   = { builtinMoveUp() },
                            onMoveDown = { builtinMoveDown() },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SpendingOverviewHeader(selectedPeriod = spendingPeriod, onPeriodSelected = onPeriodSelected)
                                val filteredCategories = computeSpendingCategories(
                                    transactions    = filteredRawTransactions,
                                    period          = spendingPeriod,
                                    displayCurrency = CurrencyController.currentCurrency,
                                    rates           = exchangeRates
                                )
                                val chartCategories = filteredCategories.filter { it.percent >= 0.01f }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DonutChart(categories = chartCategories, modifier = Modifier.size(150.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (filteredCategories.isEmpty()) {
                                            Text("No spending data yet", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            filteredCategories.forEach { cat ->
                                                CategoryLegendRow(cat)
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
                            onMoveUp   = { builtinMoveUp() },
                            onMoveDown = { builtinMoveDown() },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle(appStringResource(StringKey.DASHBOARD_MONTHLY_TREND))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    LegendDot(color = Color(0xFF16A34A), label = "In")
                                    LegendDot(color = Color(0xFFEF4444), label = "Out")
                                }
                                LineChart(data = state.monthlyTrend, modifier = Modifier.fillMaxWidth().height(160.dp))
                            }
                        }
                    }
                }

                "categories_budget_row" -> Row(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if ("top_categories" !in deletedCards) {
                        CustomizableCard(
                            cardKey = "top_categories",
                            isCustomizing = isCustomizing,
                            onDelete = { deletedCards = deletedCards + it },
                            onMoveUp   = { builtinMoveUp() },
                            onMoveDown = { builtinMoveDown() },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            HalfCardContent("top_categories", state)
                        }
                    }
                }
            }
        }

        // ── Dynamically added chart cards (desktop) — rendered above the + Charts button ──
        // desktopChartRows is computed above in Composable scope via derivedStateOf.
        itemsIndexed(desktopChartRows, key = { _, row -> "drow_${row.joinToString("|")}" }) { rowIndex, row ->

            fun desktopMoveRowUp() {
                val firstKey = row.first()
                val idx = desktopChartOrder.indexOf(firstKey)
                if (idx > 0) desktopChartOrder.move(idx, idx - 1)
            }
            fun desktopMoveRowDown() {
                val lastKey = row.last()
                val idx = desktopChartOrder.indexOf(lastKey)
                if (idx < desktopChartOrder.lastIndex) desktopChartOrder.move(idx, idx + 1)
            }

            if (row.size == 2) {
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
                            onMoveUp   = { desktopMoveRowUp() },
                            onMoveDown = { desktopMoveRowDown() },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            ChartCardContent(
                                key             = key,
                                state           = state,
                                rawTransactions = filteredRawTransactions,
                                displayCurrency = CurrencyController.currentCurrency,
                                rates           = exchangeRates
                            )
                        }
                    }
                }
            } else {
                val key = row[0]
                val def = dashboardCardDef(key)
                if (def?.size == CardSize.HALF) {
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
                            onMoveUp   = { desktopMoveRowUp() },
                            onMoveDown = { desktopMoveRowDown() },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            ChartCardContent(
                                key             = key,
                                state           = state,
                                rawTransactions = filteredRawTransactions,
                                displayCurrency = CurrencyController.currentCurrency,
                                rates           = exchangeRates
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    val cardHeight = if (key == "merchant_frequency") TREEMAP_CARD_HEIGHT else FULL_CARD_HEIGHT
                    CustomizableCard(
                        cardKey = key,
                        isCustomizing = isCustomizing,
                        onDelete = {
                            chartCardsOnDashboard = chartCardsOnDashboard - key
                            desktopChartOrder.remove(key)
                        },
                        onMoveUp   = { desktopMoveRowUp() },
                        onMoveDown = { desktopMoveRowDown() },
                        modifier = Modifier.fillMaxWidth().height(cardHeight)
                    ) {
                        ChartCardContent(
                            key             = key,
                            state           = state,
                            rawTransactions = filteredRawTransactions,
                            displayCurrency = CurrencyController.currentCurrency,
                            rates           = exchangeRates
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

        // Budget Progress — fixed, not deletable
        item {
            DashboardCard(modifier = Modifier.fillMaxWidth().height(FULL_CARD_HEIGHT)) {
                BudgetProgressCardContent(
                    api = budgetApi,
                    authToken = authToken,
                    transactions = state.rawTransactions,
                    currency = state.currency,
                    exchangeRates = exchangeRates
                )
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
                                SectionTitle(appStringResource(StringKey.DASHBOARD_RECENT_TRANSACTIONS))
                                Text(appStringResource(StringKey.DASHBOARD_VIEW_ALL), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onViewAllTransactionsClicked() })
                            }
                            if (state.recentTransactions.isEmpty()) {
                                Text(appStringResource(StringKey.TRANSACTIONS_EMPTY), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    state.recentTransactions.forEach { tx -> TransactionRow(tx) }
                                }
                            }
                        }
                    }
                }
                if ("accounts_overview" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "accounts_overview",
                        isCustomizing = false, // Accounts Overview is not deletable on desktop
                        onDelete = {},
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(appStringResource(StringKey.DASHBOARD_ACCOUNTS_OVERVIEW))
                            if (state.accounts.isEmpty()) {
                                Text(appStringResource(StringKey.DASHBOARD_NO_ACCOUNTS_TITLE), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Column(
                                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    state.accounts.forEach { account ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
                                                Text(appStringResource(StringKey.ACCOUNTS_STATUS_CONNECTED), style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF16A34A))
                                            }
                                        }
                                        if (account != state.accounts.last()) HorizontalDivider()
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = appStringResource(StringKey.DASHBOARD_ADD_ACCOUNT),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if ("quick_actions" !in deletedCards) {
                    CustomizableCard(
                        cardKey = "quick_actions",
                        isCustomizing = false, // Quick Actions is not deletable on desktop
                        onDelete = {},
                        modifier = Modifier.weight(0.8f).fillMaxHeight()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(appStringResource(StringKey.DASHBOARD_QUICK_ACTIONS))
                            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = appStringResource(StringKey.DASHBOARD_CONNECTED_ACCOUNTS),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                    )
                            }
                            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = appStringResource(StringKey.BUDGETS_ADD),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
//                            Spacer(Modifier.height(8.dp))
//                            SectionTitle(appStringResource(StringKey.DASHBOARD_UPCOMING_BILLS))
//                            Text(appStringResource(StringKey.COMMON_COMING_SOON), style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
