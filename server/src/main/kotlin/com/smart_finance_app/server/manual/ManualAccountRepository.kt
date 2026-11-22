package com.smart_finance_app.server.manual

import com.smart_finance_app.server.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal suspend fun createManualAccount(
    userId: UUID,
    account: ValidatedManualAccount
): ManualAccountResponse = withContext(Dispatchers.IO) {
    withManualAccountConnection { connection ->
        connection.prepareStatement(
            """
            INSERT INTO public.manual_accounts
                (id, user_id, name, account_type, currency, starting_balance)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id, name, account_type, currency, starting_balance,
                      starting_balance AS current_balance
            """.trimIndent()
        ).use { statement ->
            statement.queryTimeout = 10
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, userId)
            statement.setString(3, account.name)
            statement.setString(4, account.type.databaseValue)
            statement.setString(5, account.currency)
            statement.setBigDecimal(6, account.startingBalance)
            statement.executeQuery().use { result ->
                check(result.next()) { "Manual account insert returned no row" }
                result.toManualAccountResponse()
            }
        }
    }
}

internal suspend fun getManualAccountsForUser(userId: UUID) : List<ManualAccountResponse> =
    withContext(Dispatchers.IO) {
        withManualAccountConnection { connection ->
            connection.prepareStatement(
                """
                SELECT a.id, a.name, a.account_type, a.currency, a.starting_balance,
                       a.starting_balance + COALESCE(SUM(t.amount), 0) AS current_balance
                FROM public.manual_accounts a
                LEFT JOIN public.manual_transactions t
                    ON t.account_id = a.id AND t.user_id = a.user_id
                WHERE a.user_id = ?
                GROUP BY a.id
                ORDER BY a.created_at DESC, a.id DESC
                """.trimIndent()
            ).use { statement ->
                statement.queryTimeout = 10
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toManualAccountResponse())
                    }
                }
            }
        }
    }

private fun ResultSet.toManualAccountResponse(): ManualAccountResponse =
    ManualAccountResponse(
        id = getObject("id", UUID::class.java).toString(),
        name = getString("name"),
        type = ManualAccountType.entries.first { it.databaseValue == getString("account_type") },
        currency = getString("currency"),
        startingBalance = getBigDecimal("starting_balance")
            .setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        currentBalance = getBigDecimal("current_balance")
            .setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    )

private inline fun <T> withManualAccountConnection(block: (Connection) -> T): T =
    Database.dataSource.connection.use { connection ->
        try {
            connection.autoCommit = false
            val value = block(connection)
            connection.commit()
            value
        } catch (exception: Exception) {
            try {
                connection.rollback()
            } catch (rollbackFailure: Exception) {
                exception.addSuppressed(rollbackFailure)
            }
            throw exception
        }
    }