package com.smart_finance_app.server.manual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID

private const val MANUAL_TRANSACTION_COLUMNS = """
    t.id, t.account_id, t.amount, t.description, t.category,
    to_char(t.transaction_timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS transaction_date,
    t.transaction_type, a.name AS account_name, a.currency
"""

internal suspend fun createManualTransaction(
    userId: UUID,
    transaction: ValidatedManualTransaction,
    requestId: UUID
): ManualTransactionCreateResult = withContext(Dispatchers.IO) {
    val canonical = CreateManualTransactionRequest(
        accountId = transaction.accountId.toString(),
        type = transaction.type,
        amount = transaction.signedAmount.abs().toPlainString(),
        description = transaction.description,
        category = transaction.category,
        date = transaction.date.toString()
    )
    val requestHash = MessageDigest.getInstance("SHA-256")
        .digest(Json.encodeToString(canonical).toByteArray(Charsets.UTF_8))
        .let { HexFormat.of().formatHex(it) }

    withManualAccountConnection { connection ->
        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        val inserted = connection.prepareStatement(
            """
            WITH account AS (
                SELECT id, user_id, name, currency
                FROM public.manual_accounts
                WHERE user_id = ? AND id = ?
            ),
            inserted AS (
                                INSERT INTO public.manual_transactions (
                    id, user_id, account_id, amount, description, category, transaction_type,
                    transaction_timestamp, create_request_id, create_request_hash
                )
                SELECT ?, a.user_id, a.id, ?, ?, ?, ?, ?, ?, ?
                FROM account a
                WHERE true -- disambiguates ON CONFLICT from a JOIN ... ON clause
                ON CONFLICT (user_id, create_request_id) DO NOTHING
                RETURNING id, account_id, amount, description, category, transaction_type,
                          transaction_timestamp
            )
            SELECT $MANUAL_TRANSACTION_COLUMNS
            FROM inserted t
            JOIN account a ON a.id = t.account_id
            """.trimIndent()
        ).use { statement ->
            statement.queryTimeout = 10
            var parameter = 1
            statement.setObject(parameter++, userId)
            statement.setObject(parameter++, transaction.accountId)
            statement.setObject(parameter++, UUID.randomUUID())
            statement.setBigDecimal(parameter++, transaction.signedAmount)
            statement.setString(parameter++, transaction.description)
            statement.setString(parameter++, transaction.category)
            statement.setString(parameter++, transaction.type.databaseValue)
            statement.setTimestamp(
                parameter++,
                Timestamp.from(transaction.date.atStartOfDay(ZoneOffset.UTC).toInstant())
            )
            statement.setObject(parameter++, requestId)
            statement.setString(parameter, requestHash)
            statement.executeQuery().use { result ->
                if (result.next()) result.toManualTransactionResponse() else null
            }
        }
        if (inserted != null) {
            return@withManualAccountConnection ManualTransactionCreateResult(inserted, true)
        }

        connection.prepareStatement(
            """
            SELECT $MANUAL_TRANSACTION_COLUMNS, t.create_request_hash
            FROM public.manual_transactions t
            JOIN public.manual_accounts a ON a.user_id = t.user_id AND a.id = t.account_id
            WHERE t.user_id = ? AND t.create_request_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.queryTimeout = 10
            statement.setObject(1, userId)
            statement.setObject(2, requestId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw ManualTransactionAccountNotFound()
                if (result.getString("create_request_hash") != requestHash) {
                    throw ManualTransactionCreateConflict()
                }
                ManualTransactionCreateResult(result.toManualTransactionResponse(), false)
            }
        }
    }
}

internal suspend fun getManualTransactionsForUser(
    userId: UUID,
    page: Int,
    pageSize: Int,
    type: ManualTransactionType?
): ManualTransactionsPageResponse = withContext(Dispatchers.IO) {
    require(page >= 0 && pageSize in 1..100)
    val typeCondition = when (type) {
        null -> ""
        ManualTransactionType.INCOME -> "AND t.amount > 0"
        ManualTransactionType.EXPENSE -> "AND t.amount < 0"
    }
    withManualAccountConnection { connection ->
        val totalCount = connection.prepareStatement(
            "SELECT COUNT(*) FROM public.manual_transactions t WHERE t.user_id = ? $typeCondition"
        ).use { statement ->
            statement.queryTimeout = 10
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                result.next()
                result.getLong(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }

        val transactions = connection.prepareStatement(
            """
            SELECT $MANUAL_TRANSACTION_COLUMNS
            FROM public.manual_transactions t
            JOIN public.manual_accounts a ON a.user_id = t.user_id AND a.id = t.account_id
            WHERE t.user_id = ? $typeCondition
            ORDER BY t.transaction_timestamp DESC, t.created_at DESC, t.id DESC
            LIMIT ? OFFSET ?
            """.trimIndent()
        ).use { statement ->
            statement.queryTimeout = 10
            statement.setObject(1, userId)
            statement.setInt(2, pageSize)
            statement.setLong(3, page.toLong() * pageSize)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.toManualTransactionResponse()) }
            }
        }

        ManualTransactionsPageResponse(
            transactions = transactions,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount,
            hasMore = (page.toLong() + 1) * pageSize < totalCount
        )
    }
}

private fun ResultSet.toManualTransactionResponse(): ManualTransactionResponse =
    ManualTransactionResponse(
        id = getObject("id", UUID::class.java).toString(),
        date = getString("transaction_date"),
        merchantName = getString("description"),
        category = getString("category"),
        accountName = getString("account_name"),
        accountId = getObject("account_id", UUID::class.java).toString(),
        amount = getBigDecimal("amount").toDouble(),
        type = ManualTransactionType.entries.first {
            it.databaseValue == getString("transaction_type")
        },
        currency = getString("currency")
    )