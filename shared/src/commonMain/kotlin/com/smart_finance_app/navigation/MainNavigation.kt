package com.smart_finance_app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
import com.smart_finance_app.currency.CurrencyController

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
                            Text(navLabel)
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
                onNavigateToTransactions = { selected = AppNavigation.Transactions }
            )
        }
    }}

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
    onNavigateToTransactions: () -> Unit
) {
    val transactionsApi = remember(apiBaseUrl, httpClient) { TransactionsApi(apiBaseUrl, httpClient) }
    val subscriptionApi = remember(apiBaseUrl, httpClient) { SubscriptionApi(apiBaseUrl, httpClient) }
    val profileApi = remember(apiBaseUrl, httpClient) { ProfileApi(apiBaseUrl, httpClient) }
    var transactions by remember { mutableStateOf(emptyList<TransactionUI>()) }
    var transactionsLoading by remember { mutableStateOf(false) }
    var transactionsError by remember { mutableStateOf<String?>(null) }
    var transactionsPage by remember { mutableStateOf(0) }
    var transactionsHasMore by remember { mutableStateOf(false) }
    var transactionsTotalCount by remember { mutableStateOf(0) }
    var transactionsLoadedOnce by remember { mutableStateOf(false) }
    var loadingTransactionsPage by remember { mutableStateOf<Int?>(null) }
    var transactionsSyncing by remember { mutableStateOf(false) }
    var transactionsFilter by remember { mutableStateOf("All") }
    var dashboardRecentTransactions by remember { mutableStateOf(emptyList<TransactionUI>()) }
    var lastSyncedToken by remember { mutableStateOf<String?>(null) }
    var bankConnectionRefreshRequest by remember { mutableStateOf(0) }
    var categoryUpdateError by remember { mutableStateOf<String?>(null) }
    var updatingCategoryTransactionId by remember { mutableStateOf<String?>(null) }
    var exchangeRates by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    LaunchedEffect(authToken) {
        if (authToken.isNotBlank()) {
            val rates = com.smart_finance_app.currency.ExchangeRateService.getRates(
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

                    transactions = if (append) transactions + newItems else newItems
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
            when (val syncResult = transactionsApi.syncTransactions(authToken)) {
                is TransactionSyncResult.Success -> Unit

                is TransactionSyncResult.Failure -> {
                    transactionsError = AppStrings.get(
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
        when (
            val result = transactionsApi.getTransactions(
                token = authToken,
                page = 0,
                pageSize = 500,
                type = "All"
            )
        ) {
            is TransactionsResult.Success -> {
                dashboardRecentTransactions = result.page.transactions.map { transaction ->
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
            }

            is TransactionsResult.Failure -> Unit
        }
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
        transactionsError = null

        try {
            when (val syncResult = transactionsApi.syncTransactions(authToken)) {
                is TransactionSyncResult.Success -> Unit
                is TransactionSyncResult.Failure -> {
                    transactionsError = AppStrings.get(
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
            authToken                   = authToken,
            userName                    = userName,
            userId                       = userEmail,
            apiBaseUrl                   = apiBaseUrl,
            transactions                = mappedTransactions,
            onConnectAccountClicked     = onNavigateToAccounts,
            onViewAllTransactionsClicked = onNavigateToTransactions,
            api = dashboardApi,
            budgetApi = budgetApi
        )

        AppNavigation.Transactions -> {
            TransactionsScreen(
                transactions  = transactions,
                isLoading     = transactionsLoading,
                isSyncing = transactionsSyncing,
                errorMessage  = transactionsError,
                currentPage = transactionsPage,
                totalCount = transactionsTotalCount,
                pageSize = transactionsPageSize,
                hasMore = transactionsHasMore,
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
                onPageSelected = {page ->
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
            var banksLoading by remember { mutableStateOf(false) }
            var pendingConnectionState by remember { mutableStateOf<String?>(null) }

            val scope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
            val bankingApi = remember(apiBaseUrl, httpClient) { BankingApi(apiBaseUrl, httpClient) }

            LaunchedEffect(pendingConnectionState, authToken) {
                val state = pendingConnectionState ?: return@LaunchedEffect

                while (true) {
                    delay(2_000.milliseconds)

                    when (val result = bankingApi.getConnectionStatus(authToken, state)) {
                        is BankConnectionStatusResult.Success -> {
                            when (result.status) {
                                "completed" -> {
                                    pendingConnectionState = null
                                    bankConnectionRefreshRequest++
                                    onNavigateToTransactions()
                                    break
                                }

                                "failed" -> {
                                    pendingConnectionState = null
                                    error = "Bank connection failed. Please try again."
                                    break
                                }
                            }
                        }

                        is BankConnectionStatusResult.Failure -> {
                            error = AppStrings.get(
                                LocaleController.currentLanguageCode,
                                result.message
                            )
                        }
                    }
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
                        is BankProviderResult.Failure -> { banksError = AppStrings.get(
                            LocaleController.currentLanguageCode,
                            result.message
                        ) }
                    }
                    banksLoading = false
                }
            }

            LaunchedEffect(authToken, showConnectBank) {
                if (!showConnectBank && authToken.isNotBlank()) {
                    accountsLoading = true
                    accountsError = null
                    when (val result = bankingApi.getConnectedAccounts(authToken)) {
                        is ConnectedAccountResult.Success -> {
                            accounts = result.accounts.map {
                                ConnectedAccount(
                                    bankName     = it.bankName,
                                    maskedNumber = it.maskedNumber,
                                    isConnected  = true
                                )
                            }
                        }
                        is ConnectedAccountResult.Failure -> { accountsError = AppStrings.get(
                            LocaleController.currentLanguageCode,
                            result.message
                        ) }
                    }
                    accountsLoading = false
                }
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
                            when (val result = bankingApi.createConnectionSession(
                                token = authToken, bank = selectedBank
                            )) {
                                is BankConnectionResult.Success -> {
                                    pendingConnectionState = result.state
                                    uriHandler.openUri(result.authUrl)
                                }
                                is BankConnectionResult.Failure -> { error = AppStrings.get(
                                    LocaleController.currentLanguageCode,
                                    result.message
                                ) }
                            }
                            loading = false
                        }
                    }
                )
            } else {
                AccountsScreen(
                    accounts     = accounts,
                    onConnectBank = { showConnectBank = true }
                )
            }
        }

        AppNavigation.Budgets -> {
            BudgetScreen(
                authToken    = authToken,
                transactions = mappedTransactions,
                currency     = CurrencyController.currentCurrency,
                exchangeRates = exchangeRates,
                api = budgetApi
            )
        }

        AppNavigation.Settings -> {
            SettingsScreen(
                userName = userName,
                userEmail = userEmail,
                authToken = authToken,
                subscriptionApi = subscriptionApi,
                userPreferencesApi = userPreferencesApi,
                profileApi = profileApi,
                onProfileUpdated = onProfileUpdated,
                onSignOut = onSignOut
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