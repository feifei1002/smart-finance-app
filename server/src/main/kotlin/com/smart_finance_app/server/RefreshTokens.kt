package com.smart_finance_app.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private val secureRandom = SecureRandom()

data class RotatedRefreshToken(
    val userId: UUID,
    val refreshToken: String
)

fun createRefreshToken(userId: UUID): String {
    val token = generateRefreshToken()
    val tokenHash = hashRefreshToken(token)
    val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
                    VALUES (?, ?, ?)
                """.trimIndent()
            ).use {
                it.setObject(1, userId)
                it.setString(2, tokenHash)
                it.setTimestamp(3, Timestamp.from(expiresAt))
                it.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

    return token
}

fun validateRefreshToken(refreshToken: String): UUID? {
    val tokenHash = hashRefreshToken(refreshToken)

    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT user_id
                FROM refresh_tokens
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > now()
            """.trimIndent()
        ).use {
            it.setString(1, tokenHash)

            it.executeQuery().use { result ->
                if (result.next()) result.getObject("user_id", UUID::class.java) else null
            }
        }
    }
}

fun revokeRefreshToken(refreshToken: String) {
    val tokenHash = hashRefreshToken(refreshToken)

    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE refresh_tokens
                    SET revoked_at = now()
                    WHERE token_hash = ?
                """.trimIndent()
            ).use {
                it.setString(1, tokenHash)
                it.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

/*
Rotates a refresh token in one database transaction so the old token is
revoked before a new one is issued, preventing replay/race-condition reuse.
 */
fun rotateRefreshToken(refreshToken: String): RotatedRefreshToken? {
    val existingTokenHash = hashRefreshToken(refreshToken)
    val newToken = generateRefreshToken()
    val newTokenHash = hashRefreshToken(newToken)
    val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

    return Database.dataSource.connection.use { connection ->
        try {
            // Stores hashed refresh tokens used to keep users signed in after the app closes.
            // Tokens are hashed so the raw refresh token is never stored in the database.
            val userId = connection.prepareStatement(
                """
                    UPDATE refresh_tokens
                    SET revoked_at = now()
                    WHERE token_hash = ?
                      AND revoked_at IS NULL
                      AND expires_at > now()
                    RETURNING user_id
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, existingTokenHash)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getObject("user_id", UUID::class.java) else null
                }
            }

            if (userId == null) {
                connection.rollback()
                return@use null
            }

            connection.prepareStatement(
                """
                    INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
                    VALUES (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, newTokenHash)
                statement.setTimestamp(3, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }

            connection.commit()
            RotatedRefreshToken(userId, newToken)
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun generateRefreshToken(): String {
    val bytes = ByteArray(64)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun hashRefreshToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(token.encodeToByteArray())

    return Base64.getEncoder().encodeToString(digest)
}