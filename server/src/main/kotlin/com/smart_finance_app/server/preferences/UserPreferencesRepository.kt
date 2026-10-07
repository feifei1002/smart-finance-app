package com.smart_finance_app.server.preferences

import com.smart_finance_app.server.Database
import java.util.UUID

internal fun updateUserLanguage(userId: UUID, language: String): Boolean {
    return updateSinglePreference(userId, column = "language", value = language)
}

internal fun updateUserCurrency(userId: UUID, currency: String): Boolean {
    return updateSinglePreference(userId, column = "currency", value = currency)
}

internal fun updateUserTheme(userId: UUID, theme: String): Boolean {
    return updateSinglePreference(userId, column = "theme", value = theme)
}

internal fun updateUserPreferences(
    userId: UUID,
    request: UpdatePreferencesRequest
): Boolean {
    return Database.dataSource.connection.use { connection ->
        try {
            val rows = connection.prepareStatement(
                """
                UPDATE users
                SET language = ?, currency = ?, theme = ?
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, request.language)
                statement.setString(2, request.currency)
                statement.setString(3, request.theme)
                statement.setObject(4, userId)
                statement.executeUpdate()
            }

            connection.commit()
            rows > 0
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun updateSinglePreference(
    userId: UUID,
    column: String,
    value: String
): Boolean {
    require(column in setOf("language", "currency", "theme"))

    return Database.dataSource.connection.use { connection ->
        try {
            val rows = connection.prepareStatement(
                "UPDATE users SET $column = ? WHERE id = ?"
            ).use { statement ->
                statement.setString(1, value)
                statement.setObject(2, userId)
                statement.executeUpdate()
            }

            connection.commit()
            rows > 0
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}