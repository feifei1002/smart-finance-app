package com.smart_finance_app.server.banking.routes

import com.smart_finance_app.server.config.FeatureFlags
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route

fun Route.bankingRoutes() {
    authenticate("auth-jwt") {
        if (FeatureFlags.bankIntegrationEnabled) {
            bankConnectionRoutes()
            connectedAccountRoutes()
            bankTransactionRoutes()
            bankProviderRoutes()
        }
    }
    if (FeatureFlags.bankIntegrationEnabled) {
        bankCallbackRoutes()
    }
}