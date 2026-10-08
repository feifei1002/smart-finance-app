package com.smart_finance_app.server.dashboard

import com.smart_finance_app.server.Database
import java.util.UUID
import kotlin.use

private val allowedDashboardKeys = setOf(
    "spending",
    "trend",
    "top_categories",
    "weekly_spending",
    "bank_comparison",
    "time_of_day",
    "largest_tx",
    "smallest_tx",
    "merchant_frequency"
)

private val allowedHalfPositionKeys = setOf(
    "trend",
    "top_categories",
    "bank_comparison",
    "time_of_day",
    "largest_tx",
    "smallest_tx"
)

// ── Dashboard layout sync ─────────────────────────────────────────────────────

private fun cleanHalfPositions(raw: String): String =
    raw.take(500)
        .split("|")
        .mapNotNull { entry ->
            val split = entry.lastIndexOf(':')
            if (split <= 0) return@mapNotNull null

            val key = entry.substring(0, split).trim()
            val position = entry.substring(split + 1)
                .toFloatOrNull()
                ?.coerceIn(0f, 1f)
                ?: return@mapNotNull null

            if (key !in allowedHalfPositionKeys) return@mapNotNull null
            key to position
        }
        .distinctBy { it.first }
        .take(20)
        .joinToString("|") { (key, position) -> "$key:$position" }

private fun cleanKeyList(raw: String, separator: String): String =
    raw.split(separator)
        .map { it.trim() }
        .filter { it in allowedDashboardKeys }
        .distinct()
        .take(20)
        .joinToString(separator)

internal fun cleanDashboardLayout(layout: DashboardLayoutRequest): DashboardLayoutRequest =
    DashboardLayoutRequest(
        cardOrder = cleanKeyList(layout.cardOrder.take(500), ","),
        deletedCards = cleanKeyList(layout.deletedCards.take(500), "|"),
        chartCards = cleanKeyList(layout.chartCards.take(500), "|"),
        halfPositions = cleanHalfPositions(layout.halfPositions)
    )

/**
 * Returns the saved layout for [userId], or null if none exists yet.
 */
internal fun getDashboardLayout(userId: UUID): DashboardLayoutRequest? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT card_order, deleted_cards, chart_cards, half_positions
            FROM dashboard_layouts
            WHERE user_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (result.next()) DashboardLayoutRequest(
                    cardOrder     = result.getString("card_order")     ?: "",
                    deletedCards  = result.getString("deleted_cards")  ?: "",
                    chartCards    = result.getString("chart_cards")    ?: "",
                    halfPositions = result.getString("half_positions") ?: ""
                ) else null
            }
        }
    }

/**
 * Upserts the layout for [userId].  Creates the row on first save, overwrites on
 * subsequent saves — so the backend always holds the user's latest layout.
 */
internal fun saveDashboardLayout(userId: UUID, layout: DashboardLayoutRequest) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                INSERT INTO dashboard_layouts
                    (user_id, card_order, deleted_cards, chart_cards, half_positions, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET
                    card_order     = EXCLUDED.card_order,
                    deleted_cards  = EXCLUDED.deleted_cards,
                    chart_cards    = EXCLUDED.chart_cards,
                    half_positions = EXCLUDED.half_positions,
                    updated_at     = now()
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, layout.cardOrder)
                statement.setString(3, layout.deletedCards)
                statement.setString(4, layout.chartCards)
                statement.setString(5, layout.halfPositions)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}