package com.smart_finance_app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import com.smart_finance_app.accounts.AccountsScreen
import com.smart_finance_app.accounts.BankConnectionResult
import com.smart_finance_app.accounts.BankOption
import com.smart_finance_app.accounts.BankProviderResult
import com.smart_finance_app.accounts.BankingApi
import com.smart_finance_app.accounts.ConnectBankAccountScreen
import com.smart_finance_app.accounts.ConnectedAccount
import com.smart_finance_app.accounts.ConnectedAccountResult
import com.smart_finance_app.dashboard.DashboardScreen
import kotlinx.coroutines.launch


@Composable
fun MainNavigation(apiBaseUrl: String, authToken: String, userName: String, onSignOut: () -> Unit) {
    var selected by remember { mutableStateOf(AppNavigation.Dashboard) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 700.dp

        val destinations = if (compact) {
            mobileNavigations
        } else {
            AppNavigation.entries
        }

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
                                if (
                                    compact &&
                                    destination == AppNavigation.Dashboard
                                ) {
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
            NavigationContent(navigation = selected, apiBaseUrl = apiBaseUrl, authToken = authToken, userName = userName, onSignOut = onSignOut)
        }
    }
}

@Composable
private fun NavigationContent(navigation: AppNavigation, apiBaseUrl: String, authToken: String, userName: String, onSignOut: () -> Unit) {
    when (navigation) {
        AppNavigation.Dashboard -> DashboardScreen(apiBaseUrl = apiBaseUrl, authToken = authToken, userName = userName)

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
            val bankingApi = remember(apiBaseUrl) { BankingApi(apiBaseUrl) }

            DisposableEffect(bankingApi) {
                onDispose { bankingApi.close() }
            }

            LaunchedEffect(showConnectBank, authToken) {
                if (showConnectBank && authToken.isNotBlank()) {
                    banksLoading = true
                    banksError = null

                    when (val result = bankingApi.getBankProviders(authToken)) {
                        is BankProviderResult.Success -> {
                            banks = result.providers.map {
                                BankOption(
                                    id = it.id,
                                    name = it.name,
                                    logoUrl = it.logoUrl
                                )
                            }
                        }

                        is BankProviderResult.Failure -> {
                            banksError = result.message
                        }
                    }

                    banksLoading = false
                }
            }

            LaunchedEffect(authToken, showConnectBank) {
                if(!showConnectBank && authToken.isNotBlank()) {
                    accountsLoading = true
                    accountsError = null

                    when(val result = bankingApi.getConnectedAccounts(authToken)) {
                        is ConnectedAccountResult.Success -> {
                            accounts = result.accounts.map {
                                ConnectedAccount(
                                    bankName = it.bankName,
                                    maskedNumber = it.maskedNumber,
                                    isConnected = true
                                )
                            }
                        }

                        is ConnectedAccountResult.Failure -> {
                            accountsError = result.message
                        }
                    }

                    accountsLoading = false
                }
            }

            if(showConnectBank) {
                ConnectBankAccountScreen(
                    banks = banks,
                    errorMessage = error ?: banksError,
                    isLoading = loading || banksLoading,
                    onCancel = { showConnectBank = false },
                    onContinue = { selectedBank ->
                        scope.launch {
                            loading = true
                            error = null

                            when (
                                val result = bankingApi.createConnectionSession(
                                    token = authToken, bank = selectedBank
                                )
                            ) {
                                is BankConnectionResult.Success -> {
                                    uriHandler.openUri(result.authUrl)
                                }

                                is BankConnectionResult.Failure -> {
                                    error = result.message
                                }
                            }
                            loading = false
                        }
                    }
                )
            } else {
                AccountsScreen(
                    accounts = accounts,
                    onConnectBank = { showConnectBank = true }
                )
            }
        }

        AppNavigation.Settings -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onSignOut
                ) {
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