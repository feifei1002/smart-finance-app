package com.smart_finance_app.settings

import com.smart_finance_app.LocaleController
import com.smart_finance_app.StringKey
import com.smart_finance_app.getPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/** Must match MAX_MESSAGE_LENGTH on the server (SupportMessages.kt). */
internal const val SUPPORT_MESSAGE_MAX_LENGTH = 2000

@Serializable
private data class SupportMessageRequest(
    val type: String,
    val message: String,
    val rating: Int? = null,
    val category: String? = null,
    val platform: String? = null,
    val appVersion: String? = null,
    val language: String? = null
)

enum class FeedbackCategory(val apiValue: String) {
    Bug("bug"),
    Idea("idea"),
    Other("other")
}

sealed interface SendSupportMessageResult {
    data object Success : SendSupportMessageResult
    data class Failure(val message: StringKey) : SendSupportMessageResult
}

class SupportApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun sendFeedback(
        token: String,
        rating: Int?,
        category: FeedbackCategory?,
        message: String
    ): SendSupportMessageResult = send(
        token,
        SupportMessageRequest(
            type = "feedback",
            message = message.trim(),
            rating = rating,
            category = category?.apiValue,
            platform = currentPlatform(),
            language = LocaleController.currentLanguageCode
        )
    )

    suspend fun sendSupport(
        token: String,
        message: String
    ): SendSupportMessageResult = send(
        token,
        SupportMessageRequest(
            type = "support",
            message = message.trim(),
            platform = currentPlatform(),
            language = LocaleController.currentLanguageCode
        )
    )

    private suspend fun send(token: String, body: SupportMessageRequest): SendSupportMessageResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/support/messages") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status) {
                HttpStatusCode.Created,
                HttpStatusCode.OK              -> SendSupportMessageResult.Success
                HttpStatusCode.Unauthorized    -> SendSupportMessageResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                HttpStatusCode.TooManyRequests -> SendSupportMessageResult.Failure(StringKey.SUPPORT_ERROR_TOO_MANY)
                HttpStatusCode.BadRequest      -> SendSupportMessageResult.Failure(StringKey.COMMON_ERROR_INVALID_REQUEST)
                else                           -> SendSupportMessageResult.Failure(StringKey.SUPPORT_ERROR_SEND_FAILED)
            }
        } catch (_: Exception) {
            SendSupportMessageResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    private fun currentPlatform(): String? =
        runCatching { getPlatform().name }.getOrNull()
}