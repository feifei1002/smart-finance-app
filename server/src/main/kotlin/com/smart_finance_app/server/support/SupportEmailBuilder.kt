package com.smart_finance_app.server.support

import java.util.UUID

internal data class SupportEmail(
    val subject: String,
    val body: String,
    val replyTo: String
)

internal fun buildSupportEmail(
    messageId: UUID,
    userId: UUID,
    user: SupportUser,
    message: ValidatedSupportMessage
): SupportEmail {
    val shortId = messageId.toString().take(8)
    val safeName = user.fullName
        .replace(Regex("[\\r\\n]+"), " ")
        .trim()
        .ifBlank { "User" }

    val subject = when (message.type) {
        "support" -> "[Support] $safeName (#$shortId)"
        else -> "[Feedback] ${message.rating?.let { "$it/5" } ?: "No rating"} · ${message.category ?: "general"} (#$shortId)"
    }

    val body = buildString {
        appendLine("Type: ${message.type.replaceFirstChar { it.uppercase() }}")
        appendLine("From: $safeName <${user.email}>")
        appendLine("User ID: $userId")
        appendLine("Message ID: $messageId")

        if (message.type == "feedback") {
            appendLine("Rating: ${message.rating?.let { "$it/5" } ?: "—"}")
            appendLine("Category: ${message.category ?: "—"}")
        }

        appendLine("Platform: ${message.platform ?: "unknown"}")
        appendLine("App version: ${message.appVersion ?: "unknown"}")
        appendLine("Language: ${message.language ?: "unknown"}")
        appendLine()
        appendLine("Message:")
        appendLine(message.message.ifBlank { "(no message)" })
        appendLine()
        appendLine("— Reply to this email to respond to the user directly.")
    }

    return SupportEmail(
        subject = subject,
        body = body,
        replyTo = user.email
    )
}