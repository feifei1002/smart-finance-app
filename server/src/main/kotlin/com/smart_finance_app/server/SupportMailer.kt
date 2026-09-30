package com.smart_finance_app.server

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

/**
 * Sends feedback/support messages to the project Gmail inbox.
 *
 * Environment variables:
 *   SUPPORT_SMTP_USER      required  the project Gmail address (the sender)
 *   SUPPORT_SMTP_PASSWORD  required  16-character Gmail App Password (NOT the normal Gmail password)
 *   SUPPORT_INBOX          optional  where messages are delivered; defaults to SUPPORT_SMTP_USER
 *   SUPPORT_FROM_NAME      optional  display name on the email; defaults to "Smart Finance"
 *   SUPPORT_SMTP_HOST      optional  defaults to smtp.gmail.com
 *   SUPPORT_SMTP_PORT      optional  defaults to 587 (STARTTLS)
 *
 * The password only ever lives in the server's environment — never in the app or in Git.
 */
object SupportMailer {

    private fun requireEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("Missing environment variable: $name")

    // Built on first use, so a missing variable fails the request (and is logged)
    // instead of stopping the whole server from starting.
    private val session: Session by lazy {
        val username = requireEnv("SMTP_USERNAME")
        val password = requireEnv("SMTP_PASSWORD")

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.host", System.getenv("SMTP_HOST") ?: "smtp.gmail.com")
            put("mail.smtp.port", System.getenv("SMTP_PORT") ?: "587")
            // Don't let a slow mail server hang the request forever.
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }

        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        })
    }

    /**
     * Blocking call — run it on Dispatchers.IO.
     * [replyTo] is the user's email, so hitting Reply in Gmail goes straight to them.
     */
    fun send(subject: String, body: String, replyTo: String) {
        val sender = requireEnv("SMTP_USERNAME")
        val inbox = System.getenv("SUPPORT_INBOX")?.takeIf { it.isNotBlank() } ?: sender
        val fromName = System.getenv("SUPPORT_FROM_NAME")?.takeIf { it.isNotBlank() } ?: "Smart Finance"

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(sender, fromName, "UTF-8"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(inbox))
            // strict = true rejects malformed addresses
            setReplyTo(arrayOf(InternetAddress(replyTo, true)))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }

        Transport.send(message)
    }
}