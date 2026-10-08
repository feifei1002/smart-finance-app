package com.smart_finance_app.server.config

import com.auth0.jwt.algorithms.Algorithm
import com.smart_finance_app.server.banking.routes.bankingRoutes
import com.smart_finance_app.server.budget.budgetRoutes
import com.smart_finance_app.server.consentRoutes
import com.smart_finance_app.server.createRefreshToken
import com.smart_finance_app.server.dashboard.dashboardRoutes
import com.smart_finance_app.server.exchangeRatesRoutes
import com.smart_finance_app.server.passwordResetRoutes
import com.smart_finance_app.server.profile.profileRoutes
import com.smart_finance_app.server.registrationRoutes
import com.smart_finance_app.server.sessionRoutes
import com.smart_finance_app.server.signInRoutes
import com.smart_finance_app.server.subscriptions.subscriptionRoutes
import com.smart_finance_app.server.support.supportRoutes
import com.smart_finance_app.server.preferences.userPreferencesRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

internal fun Application.configureRoutes() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: error("Missing environment variable: JWT_SECRET")

    val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    routing {

        registrationRoutes(
            createAccessToken = { userId -> createJwtToken(userId, jwtIssuer, jwtAudience, jwtAlgorithm) },
            createRefreshToken = { userId -> createRefreshToken(userId) }
        )

        signInRoutes(
            createAccessToken = { userId -> createJwtToken(userId, jwtIssuer, jwtAudience, jwtAlgorithm) },
            createRefreshToken = { userId -> createRefreshToken(userId) }
        )

        sessionRoutes(
            createAccessToken = { userId -> createJwtToken(userId, jwtIssuer, jwtAudience, jwtAlgorithm) }
        )

        passwordResetRoutes()
        consentRoutes()
        bankingRoutes()
        dashboardRoutes()
        budgetRoutes()
        subscriptionRoutes()
        userPreferencesRoutes()
        profileRoutes()
        exchangeRatesRoutes()
        supportRoutes()
        healthRoutes()
    }
}