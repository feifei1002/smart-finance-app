package com.smart_finance_app.server.support

private const val MAX_MESSAGE_LENGTH = 2000
private const val MAX_META_LENGTH = 32

private val allowedMessageTypes = setOf("feedback", "support")
private val allowedFeedbackCategories = setOf("bug", "idea", "other")

internal fun validateSupportMessage(
    request: SupportMessageRequest
): Result<ValidatedSupportMessage> {
    val type = request.type.trim().lowercase()

    if (type !in allowedMessageTypes) {
        return Result.failure(IllegalArgumentException("Invalid message type"))
    }

    val message = request.message.trim()

    if (message.length > MAX_MESSAGE_LENGTH) {
        return Result.failure(IllegalArgumentException("Message is too long"))
    }

    if (type == "support" && message.isBlank()) {
        return Result.failure(IllegalArgumentException("Message is required"))
    }

    if (type == "feedback" && request.rating == null && message.isBlank()) {
        return Result.failure(IllegalArgumentException("Rating or message is required"))
    }

    val rating = if (type == "feedback") request.rating else null

    if (rating != null && rating !in 1..5) {
        return Result.failure(IllegalArgumentException("Rating must be between 1 and 5"))
    }

    val category = if (type == "feedback") {
        request.category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    } else {
        null
    }

    if (category != null && category !in allowedFeedbackCategories) {
        return Result.failure(IllegalArgumentException("Invalid feedback category"))
    }

    return Result.success(
        ValidatedSupportMessage(
            type = type,
            message = message,
            rating = rating,
            category = category,
            platform = request.platform.cleanMeta(),
            appVersion = request.appVersion.cleanMeta(),
            language = request.language.cleanMeta()
        )
    )
}

private fun String?.cleanMeta(): String? =
    this?.trim()
        ?.replace(Regex("[\\r\\n]+"), " ")
        ?.take(MAX_META_LENGTH)
        ?.takeIf { it.isNotEmpty() }
