package com.smart_finance_app.server.manual

import com.smart_finance_app.server.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.math.RoundingMode
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.HexFormat
import java.util.UUID

internal suspend fun createManualAccount(
    userId: UUID,
    account: ValidatedManualAccount,
    requestId: UUID
): ManualAccountCreateResult = withContext(Dispatchers.IO) {
    val canonical = CreateManualAccountRequest(
        account.name, account.type, account.currency,
        account.startingBalance.toPlainString()
    )
    val requestHash = MessageDigest.getInstance("SHA-256")
        .digest(Json.encodeToString(canonical).toByteArray(Charsets.UTF_8))
        .let { HexFormat.of().formatHex(it) }

    withManualAccountConnection { connection ->
        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        val inserted = connection.prepareStatement(
            """
            INSERT INTO public.manual_accounts (
                id, user_id, name, account_type, currency, starting_balance,
                create_request_id, create_request_hash
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, create_request_id) DO NOTHING
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
            statement.setObject(7, requestId)
            statement.setString(8, requestHash)
            statement.executeQuery().use { result ->
                if (result.next()) result.toManualAccountResponse() else null
            }
        }
        if (inserted != null) {
            return@withManualAccountConnection ManualAccountCreateResult(inserted, true)
        }
        connection.prepareStatement(
            """
            SELECT a.id, a.name, a.account_type, a.currency, a.starting_balance,
                   a.create_request_hash,
                   a.starting_balance + COALESCE((
                       SELECT SUM(t.amount) FROM public.manual_transactions t
                       WHERE t.user_id = a.user_id AND t.account_id = a.id
                   ), 0) AS current_balance
            FROM public.manual_accounts a
            WHERE a.user_id = ? AND a.create_request_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.queryTimeout = 10
            statement.setObject(1, userId)
            statement.setObject(2, requestId)
            statement.executeQuery().use { result ->
                check(result.next()) { "Could not resolve existing account" }
                if (result.getString("create_request_hash") != requestHash) {
                    throw ManualAccountCreateConflict()
                }
                ManualAccountCreateResult(result.toManualAccountResponse(), false)
            }
        }
    }
}

internal suspend fun getManualAccountsForUser(
    userId: UUID,
    limit: Int = 50,
    cursor: ManualAccountCursor? = null
): ManualAccountsPageResponse = withContext(Dispatchers.IO) {
    require(limit in 1..100)
    withManualAccountConnection { connection ->
        val cursorCondition = if (cursor == null) "" else "AND (created_at, id) < (?, ?)"
        val rows = connection.prepareStatement(
            """
            WITH account_page AS MATERIALIZED (
                SELECT id, user_id, name, account_type, currency, starting_balance, created_at
                FROM public.manual_accounts
                WHERE user_id = ?
                  $cursorCondition
                ORDER BY created_at DESC, id DESC
                LIMIT ?
            )
            SELECT a.id, a.name, a.account_type, a.currency, a.starting_balance, a.created_at,
                   a.starting_balance + COALESCE(b.total, 0) AS current_balance
            FROM account_page a
            LEFT JOIN LATERAL (
                SELECT SUM(t.amount) AS total
                FROM public.manual_transactions t
                WHERE t.user_id = a.user_id AND t.account_id = a.id
            ) b ON true
            ORDER BY a.created_at DESC, a.id DESC
            """.trimIndent()
        ).use { statement ->
            statement.queryTimeout = 10
            var parameter = 1
            statement.setObject(parameter++, userId)
            if (cursor != null) {
                statement.setTimestamp(parameter++, Timestamp.from(cursor.createdAt))
                statement.setObject(parameter++, cursor.id)
            }
            statement.setInt(parameter, limit + 1)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            result.toManualAccountResponse() to ManualAccountCursor(
                                result.getTimestamp("created_at").toInstant(),
                                result.getObject("id", UUID::class.java)
                            )
                        )
                    }
                }
            }
        }
        val visible = rows.take(limit)
        ManualAccountsPageResponse(
            accounts = visible.map { it.first },
            nextCursor = if (rows.size > limit) {
                encodeManualAccountCursor(visible.last().second)
            } else null
        )
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