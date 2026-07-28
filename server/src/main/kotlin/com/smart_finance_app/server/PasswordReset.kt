package com.smart_finance_app.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.respond
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.Base64

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class PasswordResetRequestResponse(val message: String)

fun Route.passwordResetRoutes() {
    post("/auth/password-reset/request") {
        val request = runCatching { call.receive<PasswordResetRequest>() }
            .getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                return@post
            }

        val email = request.email.trim().lowercase()

        if(!email.contains("@")) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid email address"))
            return@post
        }

        val genericMessage = PasswordResetRequestResponse(
            "If an account exists for this email, a password reset link has been sent."
        )

        val user = findUserByEmail(email)

        if (user == null) {
           call.respond(HttpStatusCode.OK, genericMessage)
           return@post
        }

        val rawToken = generateSecureToken()
        val tokenHash = hashToken(rawToken)
        val expiresAt = Instant.now().plusSeconds(15 * 60)
        val resetLink = "${PasswordResetConfig.resetBaseUrl}?token=$rawToken"

        runCatching {
            savePasswordResetToken(userId = user.id, tokenHash = tokenHash, expiresAt = expiresAt)
            sendPasswordResetEmail(to = user.email, resetLink = resetLink)
        }.getOrElse {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("Password reset email could not be sent. Please try again later.")
            )
            return@post
        }

        call.respond(HttpStatusCode.OK, genericMessage)
    }
}

private data class PasswordResetUser(val id: UUID, val email: String)

private object PasswordResetConfig {
    val resetBaseUrl: String
        get() = System.getenv("PASSWORD_RESET_BASE_URL")
            ?: error("Missing environment variable: PASSWORD_RESET_BASE_URL")

    val smtpHost: String
        get() = System.getenv("SMTP_HOST")
            ?: error("Missing environment variable: SMTP_HOST")

    val smtpPort: String
        get() = System.getenv("SMTP_PORT") ?: "587"

    val smtpUsername: String
        get() = System.getenv("SMTP_USERNAME")
            ?: error("Missing environment variable: SMTP_USERNAME")

    val smtpPassword: String
        get() = System.getenv("SMTP_PASSWORD")
            ?: error("Missing environment variable: SMTP_PASSWORD")

    val smtpFrom: String
        get() = System.getenv("SMTP_FROM")
            ?: smtpUsername
}

private fun findUserByEmail(email: String): PasswordResetUser? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, email FROM users WHERE email = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, email)

            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    PasswordResetUser(
                        id = result.getObject("id", UUID::class.java),
                        email = result.getString("email")
                    )
                }
            }
        }
    }

private fun savePasswordResetToken(userId: UUID, tokenHash: String, expiresAt: Instant) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                    VALUES (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, tokenHash)
                statement.setTimestamp(3, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun generateSecureToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)
}

private fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashedBytes = digest.digest(token.encodeToByteArray())
    return Base64.getEncoder().encodeToString(hashedBytes)
}

private fun sendPasswordResetEmail(to: String, resetLink: String) {
    val properties = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.host", PasswordResetConfig.smtpHost)
        put("mail.smtp.port", PasswordResetConfig.smtpPort)
    }

    val session = Session.getInstance(
        properties,
        object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication() =
                jakarta.mail.PasswordAuthentication(
                    PasswordResetConfig.smtpUsername,
                    PasswordResetConfig.smtpPassword
                )
        }
    )

    val message = MimeMessage(session).apply {
        setFrom(InternetAddress(PasswordResetConfig.smtpFrom))
        setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        subject = "Reset your Smart Finance password"
        setText(
            """
            You requested a password reset.

            Open this link to create a new password:
            $resetLink

            This link expires in 15 minutes.

            If you did not request this, you can ignore this email.
            """.trimIndent()
        )
    }

    Transport.send(message)
}

