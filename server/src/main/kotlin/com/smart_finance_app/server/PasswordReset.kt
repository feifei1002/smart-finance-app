package com.smart_finance_app.server

import at.favre.lib.crypto.bcrypt.BCrypt
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
data class PasswordResetConfirmRequest(val token: String, val newPassword: String)

@Serializable
data class PasswordResetValidateRequest(val token: String)

@Serializable
data class PasswordResetRequestResponse(val message: String)

@Serializable
data class PasswordResetConfirmResponse(val message: String)

fun Route.passwordResetRoutes() {
    post("/auth/password-reset/validate") {
        val request = call.receive<PasswordResetValidateRequest>()

        if (request.token.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Reset link is invalid or expired")
            )
            return@post
        }

        val valid = isPasswordResetTokenValid(request.token)

        if (valid) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Reset link is invalid or expired")
            )
        }
    }

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

    post("/auth/password-reset/confirm") {
        val request = call.receive<PasswordResetConfirmRequest>()

        if (request.newPassword.length < 8) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Password must be at least 8 characters")
            )
            return@post
        }

        val result = runCatching {
            resetPassword(
                token = request.token,
                newPassword = request.newPassword
            )
        }

        if (result.isSuccess) {
            call.respond(HttpStatusCode.OK, PasswordResetConfirmResponse("Password updated"))
        } else {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Reset link is invalid or expired")
            )
        }
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

private fun isPasswordResetTokenValid(token: String): Boolean {
    val tokenHash = hashToken(token)

    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id FROM password_reset_tokens
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, tokenHash)
            statement.executeQuery().use { result ->
                result.next()
            }
        }
    }
}

private fun resetPassword(token: String, newPassword: String) {
    val passwordBytes = newPassword.encodeToByteArray()

    if(newPassword.length < 8 || passwordBytes.size > 72) {
        error("Password must be between 8 and 72 characters")
    }

    val tokenHash = hashToken(token)
    val newPasswordHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())

    Database.dataSource.connection.use { connection ->
        try {
            val userId = connection.prepareStatement(
                """
                    SELECT user_id FROM password_reset_tokens
                    WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                    FOR UPDATE
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        null
                    } else {
                        result.getObject("user_id", UUID::class.java)
                    }
                }
            } ?: error("Invalid or expired reset token")

            connection.prepareStatement(
                """
                    UPDATE users SET password_hash = ? WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, newPasswordHash)
                statement.setObject(2, userId)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                """
                    UPDATE password_reset_tokens SET used_at = now()
                    WHERE user_id = ? AND used_at IS NULL
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

//private fun resetPasswordFormHtml(
//    token: String,
//    errorMessage: String? = null
//): String {
//    val errorHtml = errorMessage?.let {
//        """<p style="color:#b00020">$it</p>"""
//    }.orEmpty()
//
//    return """
//        <html>
//        <body style="font-family:sans-serif;max-width:420px;margin:60px auto;">
//            <h2>Create a new password</h2>
//            <p>Enter and confirm your new password.</p>
//
//            $errorHtml
//
//            <form method="post" action="/auth/password-reset/confirm-form">
//                <input type="hidden" name="token" value="$token" />
//
//                <label>New password</label><br/>
//                <input
//                    name="newPassword"
//                    type="password"
//                    minlength="8"
//                    required
//                    style="width:100%;padding:10px;margin:8px 0 16px;"
//                />
//
//                <label>Confirm password</label><br/>
//                <input
//                    name="confirmPassword"
//                    type="password"
//                    minlength="8"
//                    required
//                    style="width:100%;padding:10px;margin:8px 0 16px;"
//                />
//
//                <button
//                    type="submit"
//                    style="width:100%;padding:12px;"
//                >
//                    Update password
//                </button>
//            </form>
//        </body>
//        </html>
//    """.trimIndent()
//}
//
//private fun invalidResetLinkHtml(): String =
//    """
//    <html>
//    <body style="font-family:sans-serif;max-width:420px;margin:60px auto;">
//        <h2>Reset link expired</h2>
//        <p>This password reset link is invalid or has expired.</p>
//        <p>Please return to the app and request a new password reset email.</p>
//    </body>
//    </html>
//    """.trimIndent()
//
//private fun resetPasswordSuccessHtml(): String =
//    """
//    <html>
//    <body style="font-family:sans-serif;max-width:420px;margin:60px auto;">
//        <h2>Password updated</h2>
//        <p>Your password has been updated successfully.</p>
//        <p>You can now return to the app and sign in using your new password.</p>
//    </body>
//    </html>
//    """.trimIndent()