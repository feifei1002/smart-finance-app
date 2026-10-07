package com.smart_finance_app.server.budget

import kotlinx.serialization.Serializable

// ── Request / Response models ─────────────────────────────────────────────────

@Serializable
data class BudgetRequest(
    val category: String,
    val amount: Double,
    val period: String,
    val currency: String = "GBP"
)

@Serializable
data class BudgetResponse(
    val id: String,
    val category: String,
    val amount: Double,
    val period: String,
    val currency: String,
    val createdAt: String
)