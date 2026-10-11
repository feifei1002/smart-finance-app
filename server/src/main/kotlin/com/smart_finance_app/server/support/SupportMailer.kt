package com.smart_finance_app.server.support

import com.smart_finance_app.server.GmailApiMailer

/**
 * Sends feedback/support messages to the project inbox using Gmail API.
 *
 * Environment variables:
 *   GMAIL_CLIENT_ID       required
 *   GMAIL_CLIENT_SECRET   required
 *   GMAIL_REFRESH_TOKEN   required
 *   GMAIL_SENDER_EMAIL    required
 *   SUPPORT_INBOX         optional, defaults to GMAIL_SENDER_EMAIL
 *   SUPPORT_FROM_NAME     optional, defaults to ARCA Support
 */
object SupportMailer {

    private fun requireEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("Missing environment variable: $name")

    fun send(subject: String, body: String, replyTo: String) {
        val inbox = System.getenv("SUPPORT_INBOX")
            ?.takeIf { it.isNotBlank() }
            ?: requireEnv("GMAIL_SENDER_EMAIL")

        val fromName = System.getenv("SUPPORT_FROM_NAME")
            ?.takeIf { it.isNotBlank() }
            ?: "ARCA Support"

        GmailApiMailer.send(
            to = inbox,
            subject = subject,
            body = body,
            replyTo = replyTo,
            fromName = fromName
        )
    }
}