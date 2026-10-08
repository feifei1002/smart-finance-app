package com.smart_finance_app.server.support

import kotlinx.serialization.Serializable

@Serializable
data class SupportMessageRequest(
    val type: String,                 // "feedback" or "support"
    val message: String = "",
    val rating: Int? = null,          // feedback only, 1–5
    val category: String? = null,     // feedback only: "bug", "idea", "other"
    val platform: String? = null,     // e.g. "android", "ios", "web", "desktop"
    val appVersion: String? = null,
    val language: String? = null
)

@Serializable
data class SupportMessageResponse(val id: String)

internal data class ValidatedSupportMessage(
    val type: String,
    val message: String,
    val rating: Int?,
    val category: String?,
    val platform: String?,
    val appVersion: String?,
    val language: String?
)

internal data class SupportUser(
    val email: String,
    val fullName: String
)

/** Result of saving a new message as 'pending' before it is emailed. */
internal sealed interface ReserveResult {
    data class Reserved(val user: SupportUser) : ReserveResult
    data object RateLimited : ReserveResult
    data object UserNotFound : ReserveResult
}