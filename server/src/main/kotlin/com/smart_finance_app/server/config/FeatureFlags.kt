package com.smart_finance_app.server.config

internal object FeatureFlags {
    val bankIntegrationEnabled: Boolean =
        System.getenv("BANK_INTEGRATION_ENABLED")
            ?.toBooleanStrictOrNull() ?: false
}