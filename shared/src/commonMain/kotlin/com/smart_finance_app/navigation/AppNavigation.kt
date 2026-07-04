package com.smart_finance_app.navigation

import org.jetbrains.compose.resources.DrawableResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.accounts
import smart_finance_app.shared.generated.resources.budgets
import smart_finance_app.shared.generated.resources.goals
import smart_finance_app.shared.generated.resources.home
import smart_finance_app.shared.generated.resources.reports
import smart_finance_app.shared.generated.resources.settings
import smart_finance_app.shared.generated.resources.transactions

enum class AppNavigation(val label: String, val icon: DrawableResource) {
    Dashboard("Dashboard", Res.drawable.home),
    Transactions("Transactions", Res.drawable.transactions),
    Accounts("Accounts", Res.drawable.accounts),
    Budgets("Budgets", Res.drawable.budgets),
    Reports("Reports", Res.drawable.reports),
    Goals("Goals", Res.drawable.goals),
    Settings("Settings", Res.drawable.settings),
}

val mobileNavigations = listOf(
    AppNavigation.Dashboard,
    AppNavigation.Transactions,
    AppNavigation.Accounts,
    AppNavigation.Settings
)