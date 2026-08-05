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
import com.smart_finance_app.accounts.BankOption
import com.smart_finance_app.accounts.BankProviderResult
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
import com.smart_finance_app.transactions.TransactionSyncResult
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    apiBaseUrl: String,
    authToken: String,
    userName: String,
    httpClient: HttpClient,
    dashboardApi: DashboardApi,
    budgetApi: BudgetApi,
    onSignOut: () -> Unit
) {
    var selected by remember { mutableStateOf(AppNavigation.Dashboard) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        val destinations = if (compact) mobileNavigations else AppNavigation.entries

        LaunchedEffect(compact) {
            if (selected !in destinations) {
                selected = AppNavigation.Dashboard
            }
        }

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                destinations.forEach { destination ->
                    item(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destination.label
                            )
                        },
                        label = {
                            Text(
                                if (compact && destination == AppNavigation.Dashboard) {
                                    "Home"
                                } else {
                                    destination.label
                                }
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
                httpClient = httpClient,
                dashboardApi = dashboardApi,
                budgetApi = budgetApi,
                compact = compact,
                onSignOut                = onSignOut,
                onNavigateToAccounts     = { selected = AppNavigation.Accounts },
                onNavigateToTransactions = { selected = AppNavigation.Transactions }
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
    httpClient: HttpClient,
    dashboardApi: DashboardApi,
    budgetApi: BudgetApi,
    compact: Boolean,
    onSignOut: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransactions: () -> Unit
) {
    val transactionsApi = remember(apiBaseUrl, httpClient) { TransactionsApi(apiBaseUrl, httpClient) }
    var transactions by remember { mutableStateOf(emptyList<TransactionUI>()) }
    var transactionsLoading by remember { mutableStateOf(false) }
    var transactionsError by remember { mutableStateOf<String?>(null) }
    var transactionsPage by remember { mutableStateOf(0) }
    var transactionsHasMore by remember { mutableStateOf(false) }
    var transactionsTotalCount by remember { mutableStateOf(0) }
    var transactionsLoadedOnce by remember { mutableStateOf(false) }
    var transactionsNeedRefresh by remember { mutableStateOf(false) }
    var loadingTransactionsPage by remember { mutableStateOf<Int?>(null) }

    val transactionsPageSize = if (compact) 25 else 6
    val scope = rememberCoroutineScope()

    // Fetch transactions by page
    suspend fun loadTransactionsPage(page: Int, append: Boolean) {
        if (loadingTransactionsPage != null) return

        loadingTransactionsPage = page

//        transactionsLoading = transactions.isEmpty()
        transactionsLoading = true
        transactionsError = null

        try {
            when (val result =
                transactionsApi.getTransactions(authToken, page, transactionsPageSize)) {
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
                            merchantLogoUrl = transaction.merchantLogoUrl
                        )
                    }

                    transactions = if (append) transactions + newItems else newItems
                    transactionsPage = result.page.page
                    transactionsHasMore = result.page.hasMore
                    transactionsTotalCount = result.page.totalCount
                    transactionsLoadedOnce = true
                }

                is TransactionsResult.Failure -> {
                    transactionsError = result.message
                }
            }
        } finally {
            transactionsLoading = false
            loadingTransactionsPage = null
        }
    }

    LaunchedEffect(navigation, authToken, transactionsNeedRefresh) {
        if (navigation == AppNavigation.Transactions && authToken.isNotBlank()) {
            if (!transactionsLoadedOnce) {
                loadTransactionsPage(page = 0, append = false)
            }

            if (transactionsNeedRefresh) {
                when (val syncResult = transactionsApi.syncTransactions(authToken)) {
                    is TransactionSyncResult.Success -> Unit

                    is TransactionSyncResult.Failure -> {
                        transactionsError = syncResult.message
                    }
                }

                loadTransactionsPage(page = 0, append = false)
                transactionsNeedRefresh = false
            }
        }
    }

    val mappedTransactions = remember(transactions) {
        transactions.map { tx ->
            com.smart_finance_app.dashboard.TransactionData(
                transactionId = tx.id,
                timestamp = tx.dateLabel,
                description = tx.merchantName,
                amount = tx.amount,
                currency = tx.currency,
                type = if (tx.amount < 0) "DEBIT" else "CREDIT",
                merchantName = tx.merchantName
            )
        }
    }

    val resolvedCurrency = remember(transactions) {
        transactions.firstOrNull { it.currency.isNotBlank() }?.currency ?: "GBP"
    }

    when (navigation) {
        AppNavigation.Dashboard -> DashboardScreen(
            authToken                   = authToken,
            userName                    = userName,
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
                errorMessage  = transactionsError,
                currentPage = transactionsPage,
                totalCount = transactionsTotalCount,
                pageSize = transactionsPageSize,
                hasMore = transactionsHasMore,
                onLoadNextPage = {
                    if (!transactionsLoading &&
                        loadingTransactionsPage == null &&
                        transactionsHasMore) {
                        scope.launch {
                            loadTransactionsPage(
                                page = transactionsPage + 1,
                                append = compact
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

            val scope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
            val bankingApi = remember(apiBaseUrl, httpClient) { BankingApi(apiBaseUrl, httpClient) }

            LaunchedEffect(showConnectBank, authToken) {
                if (showConnectBank && authToken.isNotBlank()) {
                    banksLoading = true
                    banksError = null
                    when (val result = bankingApi.getBankProviders(authToken)) {
                        is BankProviderResult.Success -> {
                            banks = result.providers.map {
                                BankOption(id = it.id, name = it.name, logoUrl = it.logoUrl)
                            }
                        }
                        is BankProviderResult.Failure -> { banksError = result.message }
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
                        is ConnectedAccountResult.Failure -> { accountsError = result.message }
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
                                    transactionsNeedRefresh = true
                                    uriHandler.openUri(result.authUrl)
                                }
                                is BankConnectionResult.Failure -> { error = result.message }
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
                currency     = resolvedCurrency,
                api = budgetApi
            )
        }

        AppNavigation.Settings -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = onSignOut) {
                    Text("Sign out")
                }
            }
        }

        else -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(navigation.label)
        }
    }
}