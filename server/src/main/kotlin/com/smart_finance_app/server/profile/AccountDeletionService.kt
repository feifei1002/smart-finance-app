package com.smart_finance_app.server.profile

import com.smart_finance_app.server.Database
import java.util.UUID
import kotlin.use

/**
 * Tables deleted first, in this order, because other user-owned rows point at them
 * (e.g. transactions.connected_account_id -> connected_accounts.id).
 * Every other table with a user_id column is found automatically below, so new
 * tables (budgets, consents, preferences, subscriptions, support messages, …)
 * are covered without editing this list.
 */
private val orderedUserTables = listOf(
    "transactions",
    "transaction_sync_status",
    "dashboard_layouts",
    "bank_connection_sessions",
    "connected_accounts",
    "refresh_tokens"
)

internal fun deleteUserAndAllData(userId: UUID): Boolean =
    Database.dataSource.connection.use { connection ->
        try {
            // Lock the user row. Any concurrent INSERT/UPDATE into a table with a
            // FOREIGN KEY (user_id) REFERENCES users(id) must take a KEY SHARE lock on
            // this row, so it waits here and then fails once the user is gone.
            // This only protects tables that have that FK — keep every user-owned
            // table constrained (see PR notes / schema check query).
            val email = connection.prepareStatement(
                "SELECT email FROM users WHERE id = ? FOR UPDATE"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { if (it.next()) it.getString("email") else null }
            }

            if (email == null) {
                connection.rollback()
                return@use false
            }

            // Every real table in the public schema that has a user_id column.
            val discoveredTables = connection.prepareStatement(
                """
                SELECT c.table_name
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = 'public'
                  AND c.column_name = 'user_id'
                  AND t.table_type = 'BASE TABLE'
                  AND c.table_name <> 'users'
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }.toSet()

            val tablesInOrder = orderedUserTables.filter { it in discoveredTables } +
                    (discoveredTables - orderedUserTables.toSet()).sorted()

            tablesInOrder.forEach { table ->
                // Table names come from the database catalogue, never from the request.
                val quoted = "\"" + table.replace("\"", "\"\"") + "\""
                connection.prepareStatement("DELETE FROM $quoted WHERE user_id = ?").use { statement ->
                    statement.setObject(1, userId)
                    statement.executeUpdate()
                }
            }

            // auth_rate_limits has no user_id column. Rows are keyed by an identifier
            // string, e.g. the email (sign-in) or "change-password:<userId>".
            // Exact (case-insensitive) match on the whole identifier or its suffix,
            // so no other user's rows can be caught.
            connection.prepareStatement(
                """
                DELETE FROM auth_rate_limits
                WHERE lower(identifier) = lower(?)
                   OR right(identifier, char_length(?) + 1) = ':' || ?
                   OR lower(right(identifier, char_length(?) + 1)) = ':' || lower(?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, email)
                statement.setString(2, userId.toString())
                statement.setString(3, userId.toString())
                statement.setString(4, email)
                statement.setString(5, email)
                statement.executeUpdate()
            }

            connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                statement.setObject(1, userId)
                statement.executeUpdate()
            }

            connection.commit()
            true
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
