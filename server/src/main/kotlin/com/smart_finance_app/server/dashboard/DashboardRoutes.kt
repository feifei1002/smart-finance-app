package com.smart_finance_app.server.dashboard

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route

fun Route.dashboardRoutes() {
    authenticate("auth-jwt") {
        dashboardLayoutRoutes()
    }
}