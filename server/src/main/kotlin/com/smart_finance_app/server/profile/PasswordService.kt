package com.smart_finance_app.server.profile

import at.favre.lib.crypto.bcrypt.BCrypt
import com.smart_finance_app.server.Database
import java.util.UUID
import kotlin.use

internal fun updatePasswordAndRevokeSessions(userId: UUID, passwordHash: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE id = ?"
            ).use {
                it.setString(1, passwordHash)
                it.setObject(2, userId)
                it.executeUpdate()
            }

            connection.prepareStatement(
                """
                    UPDATE refresh_tokens
                    SET revoked_at = now()
                    WHERE user_id = ?
                      AND revoked_at IS NULL
                """.trimIndent()
            ).use {
                it.setObject(1, userId)
                it.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

internal fun verifyPassword(rawPassword: String, passwordHash: String): Boolean {
    return BCrypt.verifyer()
        .verify(rawPassword.toCharArray(), passwordHash)
        .verified
}

internal fun hashPassword(rawPassword: String): String {
    return BCrypt.withDefaults()
        .hashToString(12, rawPassword.toCharArray())
}