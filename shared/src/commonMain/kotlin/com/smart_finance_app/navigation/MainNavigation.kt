package com.smart_finance_app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import org.jetbrains.compose.resources.painterResource
import com.smart_finance_app.accounts.AccountsScreen
import com.smart_finance_app.dashboard.DashboardScreen


@Composable
fun MainNavigation(onSignOut: () -> Unit) {
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
            NavigationContent(navigation = selected, onSignOut = onSignOut)
        }
    }
}

@Composable
private fun NavigationContent(navigation: AppNavigation, onSignOut: () -> Unit) {
    when (navigation) {
        AppNavigation.Dashboard -> DashboardScreen()

        AppNavigation.Accounts -> AccountsScreen(
            onConnectBank = { /* TODO: launch Open Banking flow */ }
        )

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