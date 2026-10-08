package com.smart_finance_app.server.banking.routes

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route

fun Route.bankingRoutes() {
    authenticate("auth-jwt") {
        bankConnectionRoutes()
        connectedAccountRoutes()
        bankTransactionRoutes()
        bankProviderRoutes()
    }

    bankCallbackRoutes()
}