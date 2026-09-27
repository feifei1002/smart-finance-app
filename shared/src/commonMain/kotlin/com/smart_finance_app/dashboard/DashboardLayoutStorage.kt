package com.smart_finance_app.dashboard

// ── Dashboard layout persistence (multiplatform-settings) ────────────────────

internal val KEY_CARD_ORDER    = "card_order_v2"
internal val KEY_DELETED_CARDS = "deleted_cards_v2"
internal val KEY_CHART_CARDS   = "chart_cards_v2"
internal val KEY_HALF_POSITIONS = "half_card_positions_v1"
internal val KEY_MIGRATED      = "migrated_v2"          // set once after v1 cleanup
internal val DEFAULT_CARD_ORDER = "spending,trend,top_categories"

/**
 * Portable snapshot of a user's dashboard layout, sent to / received from the backend.
 * Matches [DashboardLayoutDto] in DashboardApi.kt — keep fields in sync.
 * The backend stores this keyed by userId so every platform reads the same layout.
 *
 * Backend endpoints (implemented in Banking.kt):
 *   GET  /api/dashboard/layout  → DashboardLayoutDto (or 404 if never saved)
 *   PUT  /api/dashboard/layout  → body: DashboardLayoutDto
 */

/** Encodes the card order list to a comma-separated string for storage. */
internal fun List<String>.encodeOrder(): String = joinToString(",")

/** Decodes the card order string back to a list. */
internal fun String.decodeOrder(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** Encodes a Set<String> to a pipe-separated string. */
internal fun Set<String>.encodeSet(): String = joinToString("|")

/** Decodes a pipe-separated string to a Set<String>. */
internal fun String.decodeSet(): Set<String> =
    split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

internal fun Map<String, Float>.encodeHalfPositions(): String =
    entries.joinToString("|") { (key, position) -> "$key:${position.coerceIn(0f, 1f)}" }

internal fun String.decodeHalfPositions(): Map<String, Float> = split("|").mapNotNull { entry ->
    val split = entry.lastIndexOf(':')
    if (split <= 0) null else entry.substring(0, split) to
            (entry.substring(split + 1).toFloatOrNull()?.coerceIn(0f, 1f) ?: return@mapNotNull null)
}.toMap()