package com.smart_finance_app.server.banking.config

internal fun maxAccountsForSubscription(subscriptionStatus: String?): Int {
    return when (subscriptionStatus) {
        "basic" -> 6
        else -> 2
    }
}