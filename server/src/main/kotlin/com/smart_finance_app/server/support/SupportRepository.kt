package com.smart_finance_app.server.support

import com.smart_finance_app.server.Database
import java.util.UUID
import kotlin.use

private const val MAX_MESSAGES_PER_HOUR = 5

/**
 * In one transaction: locks the user's row, checks the hourly limit, and inserts the
 * message as 'pending'. Locking the user's row means two requests from the same user
 * at the same moment are counted one after the other, so both can't slip past the limit.
 *
 * Only 'pending' and 'sent' messages count towards the limit. 'failed' ones never reached
 * the inbox, so users aren't locked out because of an email outage on our side.
 */
internal fun reserveSupportMessage(
    id: UUID,
    userId: UUID,
    message: ValidatedSupportMessage
): ReserveResult =
    Database.dataSource.connection.use { connection ->
        try {
            val user = connection.prepareStatement(
                "SELECT email, full_name FROM users WHERE id = ? FOR UPDATE"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        SupportUser(
                            email = result.getString("email"),
                            fullName = result.getString("full_name") ?: ""
                        )
                    } else null
                }
            }

            if (user == null) {
                connection.rollback()
                return@use ReserveResult.UserNotFound
            }

            val recentCount = connection.prepareStatement(
                """
                    SELECT count(*)
                    FROM support_messages
                    WHERE user_id = ?
                      AND status IN ('pending', 'sent')
                      AND created_at > now() - interval '1 hour'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getInt(1) else 0
                }
            }

            if (recentCount >= MAX_MESSAGES_PER_HOUR) {
                connection.rollback()
                return@use ReserveResult.RateLimited
            }

            connection.prepareStatement(
                """
                    INSERT INTO support_messages (
                        id, user_id, type, rating, category, message,
                        platform, app_version, language, status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, userId)
                statement.setString(3, message.type)
                if (message.rating != null) {
                    statement.setShort(4, message.rating.toShort())
                } else {
                    statement.setNull(4, java.sql.Types.SMALLINT)
                }
                statement.setString(5, message.category)
                statement.setString(6, message.message)
                statement.setString(7, message.platform)
                statement.setString(8, message.appVersion)
                statement.setString(9, message.language)
                statement.executeUpdate()
            }

            connection.commit()
            ReserveResult.Reserved(user)
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

internal fun updateSupportMessageStatus(id: UUID, status: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                "UPDATE support_messages SET status = ? WHERE id = ?"
            ).use { statement ->
                statement.setString(1, status)
                statement.setObject(2, id)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}
