package com.smart_finance_app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import com.smart_finance_app.accounts.AccountsScreen
import com.smart_finance_app.accounts.BankConnectionResult
import com.smart_finance_app.accounts.BankConnectionStatusResult
import com.smart_finance_app.accounts.BankOption
import com.smart_finance_app.accounts.BankProviderResult
import com.smart_finance_app.accounts.BankProviderVariant
import com.smart_finance_app.accounts.BankingApi
import com.smart_finance_app.accounts.ConnectBankAccountScreen
import com.smart_finance_app.accounts.ConnectedAccount
import com.smart_finance_app.accounts.ConnectedAccountResult
import com.smart_finance_app.budget.BudgetApi
import com.smart_finance_app.dashboard.DashboardScreen
import com.smart_finance_app.transactions.TransactionUI
import com.smart_finance_app.transactions.TransactionsApi
import com.smart_finance_app.transactions.TransactionsResult
import com.smart_finance_app.transactions.TransactionsScreen
import com.smart_finance_app.budget.BudgetScreen
import com.smart_finance_app.dashboard.DashboardApi
import com.smart_finance_app.payments.SubscriptionApi
import com.smart_finance_app.payments.SubscriptionStatusResult
import com.smart_finance_app.profile.ProfileApi
import com.smart_finance_app.settings.SettingsScreen
import com.smart_finance_app.transactions.TransactionSyncResult
import com.smart_finance_app.transactions.UpdateTransactionCategoryResult
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.smart_finance_app.settings.UserPreferencesApi
import com.smart_finance_app.StringKey
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import com.smart_finance_app.accounts.AccountSelectionScreen
import com.smart_finance_app.accounts.SaveSelectedAccountsResult
import com.smart_finance_app.accounts.SelectableAccountsResult
import com.smart_finance_app.accounts.SelectableBankAccountResponse
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.ConversionResult
import com.smart_finance_app.currency.CurrencyController
import com.smart_finance_app.currency.ExchangeRateService
import com.smart_finance_app.currency.getCurrencySymbol
import com.smart_finance_app.dashboard.DashboardResult
import com.smart_finance_app.dashboard.formatCurrency
import com.smart_finance_app.settings.SettingsPanel
import com.smart_finance_app.settings.SupportApi

@Composable
fun MainNavigation(
    apiBaseUrl: String,
    authToken: String,
    userName: String,
    userEmail: String,
    httpClient: HttpClient,
    dashboardApi: DashboardApi,
    budgetApi: BudgetApi,
    userPreferencesApi: UserPreferencesApi,
    onProfileUpdated: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var selected by remember { mutableStateOf(AppNavigation.Dashboard) }
    var navigateToPlan by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        val destinations = if (compact) mobileNavigations else AppNavigation.entries

        // Resolve nav labels here — inside @Composable scope, outside the lambda
        val lang = LocaleController.currentLanguageCode
        val navLabels = mapOf(
            AppNavigation.Dashboard    to AppStrings.get(lang, StringKey.NAV_DASHBOARD),
            AppNavigation.Transactions to AppStrings.get(lang, StringKey.NAV_TRANSACTIONS),
            AppNavigation.Accounts     to AppStrings.get(lang, StringKey.NAV_ACCOUNTS),
            AppNavigation.Budgets      to AppStrings.get(lang, StringKey.NAV_BUDGETS),
            AppNavigation.Reports      to AppStrings.get(lang, StringKey.NAV_REPORTS),
            AppNavigation.Goals        to AppStrings.get(lang, StringKey.NAV_GOALS),
            AppNavigation.Settings     to AppStrings.get(lang, StringKey.NAV_SETTINGS),
        )

        LaunchedEffect(compact) {
            if (selected !in destinations) {
                selected = AppNavigation.Dashboard
            }
        }

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                destinations.forEach { destination ->
                    val navLabel = navLabels[destination] ?: destination.label
                    item(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = navLabel
                            )
                        },
                        label = {
                            Text(
                                text = navLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }
            }
        ) {
            NavigationContent(
                navigation               = selected,
                apiBaseUrl               = apiBaseUrl,
                authToken                = authToken,
                userName                 = userName,
                userEmail                = userEmail,
                httpClient               = httpClient,
                dashboardApi             = dashboardApi,
                budgetApi                = budgetApi,
                userPreferencesApi       = userPreferencesApi,
                onProfileUpdated         = onProfileUpdated,
                compact                  = compact,
                onSignOut                = onSignOut,
                onNavigateToAccounts     = { selected = AppNavigation.Accounts },
                onNavigateToTransactions = { selected = AppNavigation.Transactions },
                onNavigateToSettings     = { selected = AppNavigation.Settings },
                navigateToPlan           = navigateToPlan,
                onSetNavigateToPlan      = { navigateToPlan = it }
            )
        }
    }
}

@Composable
private fun NavigationContent(
    navigation: AppNavigation,
    apiBaseUrl: String,
    authToken: String,
    userName: String,
    userEmail: String,
    httpClient: HttpClient,
    dashboardApi: DashboardApi,
    budgetApi: BudgetApi,
    userPreferencesApi: UserPreferencesApi,
    onProfileUpdated: (String, String) -> Unit,
    compact: Boolean,
    onSignOut: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    navigateToPlan: Boolean,
    onSetNavigateToPlan: (Boolean) -> Unit,
) {
    val transactionsApi = remember(apiBaseUrl, httpClient) { TransactionsApi(apiBaseUrl, httpClient) }
    val subscriptionApi = remember(apiBaseUrl, httpClient) { SubscriptionApi(apiBaseUrl, httpClient) }
    val profileApi = remember(apiBaseUrl, httpClient) { ProfileApi(apiBaseUrl, httpClient) }
    val supportApi = remember(apiBaseUrl, httpClient) { SupportApi(apiBaseUrl, httpClient) }
    var transactions by remember { mutableStateOf(emptyList<TransactionUI>()) }
    var transactionsLoading by remember { mutableStateOf(false) }
    var transactionsError by remember { mutableStateOf<String?>(null) }
    var transactionsPage by remember { mutableStateOf(0) }
    var transactionsHasMore by remember { mutableStateOf(false) }
    var transactionsTotalCount by remember { mutableStateOf(0) }
    var transactionsLoadedOnce by remember { mutableStateOf(false) }
    var loadingTransactionsPage by remember { mutableStateOf<Int?>(null) }
    var transactionsSyncing by remember { mutableStateOf(false) }
    var transactionsSyncError by remember { mutableStateOf<String?>(null) }
    var transactionsFilter by remember { mutableStateOf("All") }
    var dashboardRecentTransactions by remember { mutableStateOf(emptyList<TransactionUI>()) }
    var lastSyncedToken by remember { mutableStateOf<String?>(null) }
    var bankConnectionRefreshRequest by remember { mutableStateOf(0) }
    var categoryUpdateError by remember { mutableStateOf<String?>(null) }
    var updatingCategoryTransactionId by remember { mutableStateOf<String?>(null) }
    var exchangeRates by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    LaunchedEffect(authToken) {
        if (authToken.isNotBlank()) {
            val rates = ExchangeRateService.getRates(
                dashboardApi.client,
                apiBaseUrl
            )
            if (rates.isNotEmpty()) {
                exchangeRates = rates
            }
        }
    }

    val transactionsPageSize = if (compact) 25 else 6
    val scope = rememberCoroutineScope()

    fun updateTransactionCategoryLocally(transactionId: String, category: String) {
        transactions = transactions.map {
            if (it.id == transactionId) it.copy(category = category) else it
        }

        dashboardRecentTransactions = dashboardRecentTransactions.map {
            if (it.id == transactionId) it.copy(category = category) else it
        }
    }

    // Fetch transactions by page
    suspend fun loadTransactionsPage(page: Int, append: Boolean) {
        if (loadingTransactionsPage != null) return

        loadingTransactionsPage = page

        transactionsLoading = true
        transactionsError = null

        try {
            when (val result =
                transactionsApi.getTransactions(
                    token = authToken,
                    page = page,
                    pageSize = transactionsPageSize,
                    type = transactionsFilter
                )) {
                is TransactionsResult.Success -> {
                    val newItems = result.page.transactions.map { transaction ->
                        TransactionUI(
                            id = transaction.id,
                            dateLabel = transaction.date.take(10),
                            merchantName = transaction.merchantName,
                            category = transaction.category,
                            accountName = transaction.accountName,
                            amount = transaction.amount,
                            currency = transaction.currency,
                            merchantLogoUrl = transaction.merchantLogoUrl,
                            accountId = transaction.accountId
                        )
                    }

                    transactions = if (append) {
                        (transactions + newItems).distinctBy { it.id }
                    } else {
                        newItems.distinctBy { it.id }
                    }
                    transactionsPage = result.page.page
                    transactionsHasMore = result.page.hasMore
                    transactionsTotalCount = result.page.totalCount
                    transactionsLoadedOnce = true
                }

                is TransactionsResult.Failure -> {
                    transactionsError = AppStrings.get(
                        LocaleController.currentLanguageCode,
                        result.message
                    )
                }
            }
        } finally {
            transactionsLoading = false
            loadingTransactionsPage = null
        }
    }

    LaunchedEffect(navigation, authToken, transactionsFilter) {
        if (navigation == AppNavigation.Transactions && authToken.isNotBlank()) {
            if (!transactionsLoadedOnce) {
                loadTransactionsPage(page = 0, append = false)
            }
        }
    }

    LaunchedEffect(authToken) {
        if (authToken.isBlank()) {
            lastSyncedToken = null
            return@LaunchedEffect
        }

        if (lastSyncedToken == authToken) {
            return@LaunchedEffect
        }

        lastSyncedToken = authToken
        transactionsSyncing = true

        try {
            transactionsSyncError = when (val syncResult = transactionsApi.syncTransactions(authToken)) {
                is TransactionSyncResult.Success -> {
                    null
                }

                is TransactionSyncResult.Failure -> {
                    AppStrings.get(
                        LocaleController.currentLanguageCode,
                        syncResult.message
                    )
                }
            }

            transactions = emptyList()
            transactionsPage = 0
            transactionsLoadedOnce = false

            loadTransactionsPage(page = 0, append = false)
        } finally {
            transactionsSyncing = false
        }
    }

    suspend fun loadDashboardTransactions() {
        val pageSize = 500
        val allTransactions = mutableListOf<TransactionUI>()
        var page = 0

        while (true) {
            when (
                val result = transactionsApi.getTransactions(
                    token = authToken,
                    page = page,
                    pageSize = pageSize,
                    type = "All"
                )
            ) {
                is TransactionsResult.Success -> {
                    allTransactions += result.page.transactions.map { transaction ->
                        TransactionUI(
                            id = transaction.id,
                            dateLabel = transaction.date,
                            merchantName = transaction.merchantName,
                            category = transaction.category,
                            accountName = transaction.accountName,
                            amount = transaction.amount,
                            currency = transaction.currency,
                            merchantLogoUrl = transaction.merchantLogoUrl,
                            accountId = transaction.accountId
                        )
                    }

                    if (!result.page.hasMore) break
                    page++
                }

                is TransactionsResult.Failure -> break
            }
        }

        dashboardRecentTransactions = allTransactions
    }

    LaunchedEffect(authToken) {
        if (authToken.isNotBlank()) {
            loadDashboardTransactions()
        }
    }

    val mappedTransactions = remember(dashboardRecentTransactions) {
        dashboardRecentTransactions.map { tx ->
            com.smart_finance_app.dashboard.TransactionData(
                transactionId = tx.id,
                timestamp = tx.dateLabel,
                description = tx.merchantName,
                amount = tx.amount,
                currency = tx.currency,
                type = if (tx.amount < 0) "DEBIT" else "CREDIT",
                merchantName = tx.merchantName,
                category = tx.category,
                accountId = tx.accountId
            )
        }
    }

    suspend fun syncAndReloadTransactions() {
        transactionsSyncing = true
        transactionsSyncError = null

        try {
            when (val syncResult = transactionsApi.syncTransactions(authToken)) {
                is TransactionSyncResult.Success -> {
                    transactionsSyncError = null
                }
                is TransactionSyncResult.Failure -> {
                    transactionsSyncError  = AppStrings.get(
                        LocaleController.currentLanguageCode,
                        syncResult.message
                    )
                }
            }

            transactions = emptyList()
            transactionsPage = 0
            transactionsLoadedOnce = false

            loadTransactionsPage(page = 0, append = false)
            loadDashboardTransactions()
        } finally {
            transactionsSyncing = false
        }
    }

    LaunchedEffect(bankConnectionRefreshRequest) {
        if (bankConnectionRefreshRequest > 0 && authToken.isNotBlank()) {
            syncAndReloadTransactions()
        }
    }

    when (navigation) {
        AppNavigation.Dashboard -> DashboardScreen(
            authToken                    = authToken,
            userName                     = userName,
            userId                       = userEmail,
            apiBaseUrl                   = apiBaseUrl,
            transactions                 = mappedTransactions,
            onConnectAccountClicked      = onNavigateToAccounts,
            onViewAllTransactionsClicked = onNavigateToTransactions,
            api                          = dashboardApi,
            budgetApi                    = budgetApi
        )

        AppNavigation.Transactions -> {
            TransactionsScreen(
                transactions  = transactions,
                isLoading     = transactionsLoading,
                isSyncing     = transactionsSyncing,
                errorMessage  = transactionsSyncError ?: transactionsError,
                currentPage   = transactionsPage,
                totalCount    = transactionsTotalCount,
                pageSize      = transactionsPageSize,
                hasMore       = transactionsHasMore,
                selectedFilter = transactionsFilter,
                onFilterSelected = { filter ->
                    transactionsFilter = filter
                    transactions = emptyList()
                    transactionsPage = 0
                    transactionsLoadedOnce = false

                    scope.launch {
                        loadTransactionsPage(page = 0, append = false)
                    }
                },
                onLoadNextPage = {
                    if (transactionsHasMore && loadingTransactionsPage == null) {
                        scope.launch {
                            loadTransactionsPage(
                                page = transactionsPage + 1,
                                append = true
                            )
                        }
                    }
                },
                onPageSelected = { page ->
                    scope.launch {
                        loadTransactionsPage(
                            page = page,
                            append = false
                        )
                    }
                },
                isUpdatingCategory = updatingCategoryTransactionId != null,
                categoryUpdateError = categoryUpdateError,
                onDismissCategoryUpdateError = {
                    categoryUpdateError = null
                },
                onUpdateCategory = { transactionId, category ->
                    updatingCategoryTransactionId = transactionId
                    categoryUpdateError = null

                    val success = when (val result = transactionsApi.updateTransactionCategory(authToken, transactionId, category)) {
                        is UpdateTransactionCategoryResult.Success -> {
                            updateTransactionCategoryLocally(transactionId, category)
                            true
                        }
                        is UpdateTransactionCategoryResult.Failure -> {
                            categoryUpdateError = result.message
                            false
                        }
                    }

                    updatingCategoryTransactionId = null
                    success
                }
            )
        }

        AppNavigation.Accounts -> {
            var showConnectBank by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var loading by remember { mutableStateOf(false) }
            var accounts by remember { mutableStateOf<List<ConnectedAccount>>(emptyList()) }
            var accountsError by remember { mutableStateOf<String?>(null) }
            var accountsLoading by remember { mutableStateOf(false) }
            var banks by remember { mutableStateOf<List<BankOption>>(emptyList()) }
            var banksError by remember { mutableStateOf<String?>(null) }
            var showAccountLimitDialog by remember { mutableStateOf(false) }
            var banksLoading by remember { mutableStateOf(false) }
            var pendingConnectionState by remember { mutableStateOf<String?>(null) }
            var subscriptionStatus by remember { mutableStateOf("free") }
            var accountSelectionState by remember { mutableStateOf<String?>(null) }
            var selectableAccounts by remember { mutableStateOf<List<SelectableBankAccountResponse>>(emptyList()) }
            var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }
            var accountSelectionError by remember { mutableStateOf<String?>(null) }
            var accountSelectionLoading by remember { mutableStateOf(false) }
            var savingSelectedAccounts by remember { mutableStateOf(false) }
            var accountsRefreshRequest by remember { mutableStateOf(0) }

            val scope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
            val bankingApi = remember(apiBaseUrl, httpClient) { BankingApi(apiBaseUrl, httpClient) }
            val maxAccounts = when (subscriptionStatus) {
                "basic" -> 6
                else -> 2
            }
            val remainingAccountSlots = (maxAccounts - accounts.size).coerceAtLeast(0)

            LaunchedEffect(authToken) {
                if (authToken.isNotBlank()) {
                    when (val result = subscriptionApi.getStatus(authToken)) {
                        is SubscriptionStatusResult.Success -> subscriptionStatus = result.status
                        is SubscriptionStatusResult.Failure -> Unit // default "free" is safe
                    }
                }
            }

            LaunchedEffect(pendingConnectionState, authToken) {
                val state = pendingConnectionState ?: return@LaunchedEffect

                var finished = false

                repeat(60) {
                    if (finished) return@repeat

                    delay(2_000.milliseconds)

                    when (val result = bankingApi.getConnectionStatus(authToken, state)) {
                        is BankConnectionStatusResult.Success -> {
                            when (result.status) {
                                "completed" -> {
                                    pendingConnectionState = null
                                    error = null
                                    banksError = null
                                    showConnectBank = false
                                    accountsRefreshRequest++
                                    bankConnectionRefreshRequest++
                                    onNavigateToTransactions()
                                    finished = true
                                }

                                "awaiting_account_selection" -> {
                                    pendingConnectionState = null
                                    showConnectBank = false
                                    error = null
                                    banksError = null
                                    accountSelectionState = state
                                    selectedAccountIds = emptySet()
                                    accountSelectionError = null
                                    finished = true
                                }

                                "failed" -> {
                                    pendingConnectionState = null
                                    error = AppStrings.get(
                                        LocaleController.currentLanguageCode,
                                        StringKey.CONNECT_BANK_FAILED
                                    )
                                    finished = true
                                }
                            }
                        }
                        
                        is BankConnectionStatusResult.Failure -> Unit
                    }
                }

                if (!finished) {
                    pendingConnectionState = null
                    error = AppStrings.get(
                        LocaleController.currentLanguageCode,
                        StringKey.CONNECT_BANK_TIMED_OUT
                    )
                }
            }

            LaunchedEffect(showConnectBank, authToken) {
                if (showConnectBank && authToken.isNotBlank()) {
                    banksLoading = true
                    banksError = null
                    when (val result = bankingApi.getBankProviders(authToken)) {
                        is BankProviderResult.Success -> {
                            banks = result.providers.map { provider ->
                                BankOption(
                                    id = provider.id,
                                    name = provider.name,
                                    logoUrl = provider.logoUrl,
                                    variants = provider.variants.map { variant ->
                                        BankProviderVariant(
                                            id = variant.id,
                                            label = variant.label,
                                            name = variant.name
                                        )
                                    }
                                )
                            }
                        }
                        is BankProviderResult.Failure -> {
                            banksError = AppStrings.get(
                                LocaleController.currentLanguageCode,
                                result.message
                            )
                        }
                    }
                    banksLoading = false
                }
            }

            LaunchedEffect(accountSelectionState, authToken) {
                val state = accountSelectionState ?: return@LaunchedEffect
                if (authToken.isBlank()) return@LaunchedEffect

                accountSelectionLoading = true
                accountSelectionError = null

                when (val result = bankingApi.getSelectableAccounts(authToken, state)) {
                    is SelectableAccountsResult.Success -> {
                        selectableAccounts = result.accounts
                        selectedAccountIds = emptySet()
                    }

                    is SelectableAccountsResult.Failure -> {
                        accountSelectionError = AppStrings.get(
                            LocaleController.currentLanguageCode,
                            result.message
                        )
                    }
                }

                accountSelectionLoading = false
            }

            LaunchedEffect(
                authToken,
                showConnectBank,
                accountsRefreshRequest,
                CurrencyController.currentCurrency,
                exchangeRates
            ) {
                if (!showConnectBank && authToken.isNotBlank()) {
                    accountsLoading = true
                    accountsError = null
                    when (val result = bankingApi.getConnectedAccounts(authToken)) {
                        is ConnectedAccountResult.Success -> {
                            val balancesByAccountId = when (val balancesResult = dashboardApi.getBalances(authToken)) {
                                is DashboardResult.Success -> balancesResult.data.associateBy { it.accountId }
                                is DashboardResult.Failure -> emptyMap()
                            }

                            accounts = result.accounts.map { account ->
                                val balance = balancesByAccountId[account.accountId]

                                val balanceText = balance?.let {
                                    when (val conversion = ExchangeRateService.convert(
                                        amount = it.current,
                                        fromCurrency = it.currency,
                                        toCurrency = CurrencyController.currentCurrency,
                                        rates = exchangeRates
                                    )) {
                                        is ConversionResult.Success -> {
                                            formatCurrency(
                                                conversion.amount,
                                                getCurrencySymbol(CurrencyController.currentCurrency),
                                                CurrencyController.currentCurrency
                                            )
                                        }

                                        else -> {
                                            formatCurrency(
                                                it.current,
                                                getCurrencySymbol(it.currency),
                                                it.currency
                                            )
                                        }
                                    }
                                }

                                ConnectedAccount(
                                    accountId = account.accountId,
                                    bankName = account.bankName,
                                    maskedNumber = account.maskedNumber,
                                    balance = balanceText,
                                    isConnected = true
                                )
                            }
                        }
                        is ConnectedAccountResult.Failure -> {
                            accountsError = AppStrings.get(
                                LocaleController.currentLanguageCode,
                                result.message
                            )
                        }
                    }
                    accountsLoading = false
                }
            }

            if (showAccountLimitDialog) {
                AccountLimitDialog(
                    onDismiss = { showAccountLimitDialog = false },
                    onLearnMore = {
                        showAccountLimitDialog = false
                        onSetNavigateToPlan(true)   // hoist flag up to MainNavigation
                        onNavigateToSettings()       // hoist navigation up to MainNavigation
                    }
                )
            }

            if (showConnectBank) {
                ConnectBankAccountScreen(
                    banks         = banks,
                    errorMessage  = error ?: banksError,
                    isLoading     = loading || banksLoading,
                    onCancel      = { showConnectBank = false },
                    onContinue    = { selectedBank ->
                        scope.launch {
                            loading = true
                            error = null
                            banksError = null

                            when (val result = bankingApi.createConnectionSession(
                                token = authToken,
                                bank = selectedBank
                            )) {
                                is BankConnectionResult.Success -> {
                                    pendingConnectionState = result.state
                                    uriHandler.openUri(result.authUrl)
                                }

                                is BankConnectionResult.Failure -> {
                                    error = AppStrings.get(
                                        LocaleController.currentLanguageCode,
                                        result.message
                                    )
                                }

                                BankConnectionResult.AccountLimitReached -> {
                                    showAccountLimitDialog = true
                                }
                            }

                            loading = false
                        }
                    }
                )
            } else if (accountSelectionState != null) {
                AccountSelectionScreen(
                    accounts = selectableAccounts,
                    selectedAccountIds = selectedAccountIds,
                    remainingSlots = remainingAccountSlots,
                    isLoading = accountSelectionLoading,
                    isSaving = savingSelectedAccounts,
                    errorMessage = accountSelectionError,
                    onBack = {
                        val state = accountSelectionState

                        if (state != null) {
                            scope.launch {
                                bankingApi.cancelAccountSelection(authToken, state)
                            }
                        }

                        accountSelectionState = null
                        selectableAccounts = emptyList()
                        selectedAccountIds = emptySet()
                    },
                    onToggleAccount = { accountId ->
                        selectedAccountIds =
                            if (accountId in selectedAccountIds) {
                                selectedAccountIds - accountId
                            } else if (selectedAccountIds.size < remainingAccountSlots) {
                                selectedAccountIds + accountId
                            } else {
                                selectedAccountIds
                            }
                    },
                    onConfirm = {
                        val state = accountSelectionState ?: return@AccountSelectionScreen

                        scope.launch {
                            savingSelectedAccounts = true
                            accountSelectionError = null

                            try {
                                when (val result = bankingApi.saveSelectedAccounts(
                                    token = authToken,
                                    state = state,
                                    accountIds = selectedAccountIds.toList()
                                )) {
                                    SaveSelectedAccountsResult.Success -> {
                                        accountSelectionState = null
                                        selectableAccounts = emptyList()
                                        selectedAccountIds = emptySet()
                                        accountsRefreshRequest++
                                        bankConnectionRefreshRequest++
                                    }

                                    is SaveSelectedAccountsResult.Failure -> {
                                        accountSelectionError = AppStrings.get(
                                            LocaleController.currentLanguageCode,
                                            result.message
                                        )
                                    }
                                }
                            } finally {
                                savingSelectedAccounts = false
                            }
                        }
                    }
                )
            } else {
                AccountsScreen(
                    accounts = accounts,
                    onConnectBank = { showConnectBank = true },
                    onDisconnectAccount = { account ->
                        bankingApi.disconnectAccount(authToken, account.accountId)
                    },
                    onAccountDisconnected = { accountId ->
                        accounts = accounts.filterNot { it.accountId == accountId }
                    }
                )
            }
        }

        AppNavigation.Budgets -> {
            BudgetScreen(
                authToken     = authToken,
                transactions  = mappedTransactions,
                currency      = CurrencyController.currentCurrency,
                exchangeRates = exchangeRates,
                api           = budgetApi
            )
        }

        AppNavigation.Settings -> {
            LaunchedEffect(Unit) {
                onSetNavigateToPlan(false)  // reset so normal Settings visits open Main
            }
            SettingsScreen(
                userName           = userName,
                userEmail          = userEmail,
                authToken          = authToken,
                subscriptionApi    = subscriptionApi,
                userPreferencesApi = userPreferencesApi,
                profileApi         = profileApi,
                supportApi         = supportApi,
                onProfileUpdated   = onProfileUpdated,
                onSignOut          = onSignOut,
                initialPanel       = if (navigateToPlan) SettingsPanel.SubscriptionPlan
                else SettingsPanel.Main
            )
        }

        else -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(navigation.label)
        }
    }
}

@Composable
private fun AccountLimitDialog(
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = appStringResource(StringKey.ACCOUNT_LIMIT_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = appStringResource(StringKey.ACCOUNT_LIMIT_BODY),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onLearnMore()
            }) {
                Text(appStringResource(StringKey.ACCOUNT_LIMIT_LEARN_MORE))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(appStringResource(StringKey.ACCOUNT_LIMIT_DISMISS))
            }
        }
    )
}