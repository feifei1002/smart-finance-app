package com.smart_finance_app.server.dashboard

import kotlinx.serialization.Serializable

// ── Dashboard layout sync ─────────────────────────────────────────────────────

@Serializable
data class DashboardLayoutRequest(
    val cardOrder: String    = "",   // comma-separated built-in card order
    val deletedCards: String = "",   // pipe-separated deleted card keys
    val chartCards: String   = "",   // pipe-separated chart card keys on dashboard
    val halfPositions: String = ""   // "key:float|…" encoded half-card positions
)