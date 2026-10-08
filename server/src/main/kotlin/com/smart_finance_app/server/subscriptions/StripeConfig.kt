package com.smart_finance_app.server.subscriptions

internal object StripeConfig {
    val secretKey: String get() = System.getenv("STRIPE_SECRET_KEY")
        ?: error("Missing environment variable: STRIPE_SECRET_KEY")

    val webhookSecret: String get() = System.getenv("STRIPE_WEBHOOK_SECRET")
        ?: error("Missing environment variable: STRIPE_WEBHOOK_SECRET")

    val proPriceId: String get() = System.getenv("STRIPE_BASIC_PRICE_ID")
        ?: error("Missing environment variable: STRIPE_BASIC_PRICE_ID")

    val successUrl: String get() = System.getenv("STRIPE_SUCCESS_URL")
        ?: "http://localhost:8080/subscription/success"

    val cancelUrl: String get() = System.getenv("STRIPE_CANCEL_URL")
        ?: "http://localhost:8080/subscription/cancel"

    val portalReturnUrl: String get() = System.getenv("STRIPE_PORTAL_RETURN_URL")
        ?: "http://localhost:8080/subscription/success"
}