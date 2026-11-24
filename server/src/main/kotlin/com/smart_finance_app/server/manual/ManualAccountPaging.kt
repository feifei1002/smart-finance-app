package com.smart_finance_app.server.manual

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal data class ManualAccountCursor(val createdAt: Instant, val id: UUID)

@Serializable
private data class CursorPayload(val createdAt: String, val id: String)

internal fun parseManualAccountLimit(raw: String?): Int {
    if (raw == null) return 50
    val limit = raw.toIntOrNull()
    require(limit != null && limit in 1..100) { "limit must be between 1 and 100" }
    return limit
}

internal fun parseManualAccountRequestId(raw: String?): UUID {
    require(raw != null && raw.length == 36) { "A UUID Idempotency-Key header is required" }
    val id = UUID.fromString(raw)
    require(id.toString().equals(raw, ignoreCase = true)) { "Invalid Idempotency-Key" }
    return id
}

internal fun encodeManualAccountCursor(cursor: ManualAccountCursor): String {
    val json = Json.encodeToString(CursorPayload(cursor.createdAt.toString(), cursor.id.toString()))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
}

internal fun parseManualAccountCursor(raw: String?): ManualAccountCursor? {
    if (raw == null) return null
    require(raw.length in 1..512 && raw.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid cursor" }
    return try {
        val json = String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        val payload = Json.decodeFromString<CursorPayload>(json)
        val instant = Instant.parse(payload.createdAt)
        require(instant >= Instant.parse("0001-01-01T00:00:00Z") &&
                instant <= Instant.parse("9999-12-31T23:59:59.999999Z"))
        val id = UUID.fromString(payload.id)
        require(payload.id == id.toString())
        ManualAccountCursor(instant, id)
    } catch (exception: Exception) {
        throw IllegalArgumentException("Invalid cursor", exception)
    }
}
