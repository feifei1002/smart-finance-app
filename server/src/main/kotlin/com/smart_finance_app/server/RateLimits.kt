package com.smart_finance_app.server

private const val MAX_FAILED_ATTEMPTS = 5
private const val WINDOW_MINUTES = 10
private const val LOCK_MINUTES = 10

fun isRateLimited(identifier: String, action: String): Boolean =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT locked_until
                FROM auth_rate_limits
                WHERE identifier = ?
                  AND action = ?
                  AND locked_until IS NOT NULL
                  AND locked_until > now()
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, identifier)
            statement.setString(2, action)

            statement.executeQuery().use { result ->
                result.next()
            }
        }
    }

fun recordFailedAttempt(identifier: String, action: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO auth_rate_limits (
                        identifier, action, failed_count, window_started_at, locked_until, updated_at
                    )
                    VALUES (?, ?, 1, now(), NULL, now())
                    ON CONFLICT (identifier, action)
                    DO UPDATE SET
                        failed_count = CASE
                            WHEN auth_rate_limits.window_started_at < now() - (? * interval '1 minute')
                            THEN 1
                            ELSE auth_rate_limits.failed_count + 1
                        END,
                        window_started_at = CASE
                            WHEN auth_rate_limits.window_started_at < now() - (? * interval '1 minute')
                            THEN now()
                            ELSE auth_rate_limits.window_started_at
                        END,
                        locked_until = CASE
                            WHEN (
                                CASE
                                    WHEN auth_rate_limits.window_started_at < now() - (? * interval '1 minute')
                                    THEN 1
                                    ELSE auth_rate_limits.failed_count + 1
                                END
                            ) >= ?
                            THEN now() + (? * interval '1 minute')
                            ELSE NULL
                        END,
                        updated_at = now()
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, identifier)
                statement.setString(2, action)
                statement.setInt(3, WINDOW_MINUTES)
                statement.setInt(4, WINDOW_MINUTES)
                statement.setInt(5, WINDOW_MINUTES)
                statement.setInt(6, MAX_FAILED_ATTEMPTS)
                statement.setInt(7, LOCK_MINUTES)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

fun clearFailedAttempts(identifier: String, action: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    DELETE FROM auth_rate_limits
                    WHERE identifier = ?
                      AND action = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, identifier)
                statement.setString(2, action)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}