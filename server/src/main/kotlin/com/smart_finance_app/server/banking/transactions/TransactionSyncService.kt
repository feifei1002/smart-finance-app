package com.smart_finance_app.server.banking.transactions

import com.smart_finance_app.server.Database
import com.smart_finance_app.server.banking.accounts.getStoredAccountsWithTokens
import com.smart_finance_app.server.banking.merchants.merchantNameForLogoLookup
import com.smart_finance_app.server.banking.models.ImportedTransactionResponse
import com.smart_finance_app.server.banking.models.PaginatedTransactionsResponse
import com.smart_finance_app.server.banking.models.StoredAccount
import com.smart_finance_app.server.banking.models.TransactionResponse
import com.smart_finance_app.server.banking.models.TransactionSyncResponse
import com.smart_finance_app.server.banking.merchants.resolveMerchantLogoUrl
import com.smart_finance_app.server.banking.truelayer.ensureFreshToken
import com.smart_finance_app.server.banking.truelayer.fetchPendingTransactions
import com.smart_finance_app.server.banking.truelayer.fetchTransactions
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.use

suspend fun syncTransactionsForUser(userId: UUID): TransactionSyncResponse {

    var importedCount = 0
    var duplicateCount = 0
    var succeededAccounts = 0

    return try {
        val storedAccounts = getStoredAccountsWithTokens(userId)
        val failedAccounts = mutableListOf<String>()

        storedAccounts.forEach { account ->
            runCatching {
                val token = ensureFreshToken(account)

                val settledTransactions = fetchTransactions(token, account.accountId)

                val pendingFetchResult = runCatching {
                    fetchPendingTransactions(token, account.accountId)
                }

                val pendingTransactions = pendingFetchResult.getOrElse {
                    emptyList()
                }

                val transactions = settledTransactions + pendingTransactions

                succeededAccounts++

                transactions.forEach { transaction ->
                    val inserted = saveImportedTransaction(userId, account, transaction)

                    if (inserted) {
                        importedCount++
                    } else {
                        duplicateCount++
                    }
                }

                pendingFetchResult.onSuccess { latestPendingTransactions ->
                    deleteStalePendingTransactions(
                        userId = userId,
                        account = account,
                        latestPendingTransactionIds = latestPendingTransactions
                            .map { it.transactionId }
                            .toSet()
                    )
                }
            }.onFailure { exception ->
                failedAccounts.add("${account.accountName}: ${exception.message ?: "Unknown error"}")
            }
        }

        if (succeededAccounts == 0 && failedAccounts.isNotEmpty()) {
            val combinedError = failedAccounts.joinToString("; ")

            if (combinedError.contains("sca_exceeded", ignoreCase = true)) {
                error("BANK_REAUTH_REQUIRED")
            }

            error("All account syncs failed: $combinedError")
        }

        val syncedAt = Instant.now()

        if (failedAccounts.isEmpty()) {
            recordTransactionSyncSuccess(userId, syncedAt)
        } else {
            recordTransactionSyncPartialFailure(
                userId = userId,
                attemptedAt = syncedAt,
                error = "Some account syncs failed: ${failedAccounts.joinToString("; ")}"
            )
        }

        TransactionSyncResponse(
            importedCount = importedCount,
            duplicateCount = duplicateCount,
            lastSuccessfulSyncAt = if (failedAccounts.isEmpty()) syncedAt.toString() else getLastSuccessfulTransactionSync(userId)
        )
    } catch (exception: Exception) {
        recordTransactionSyncFailure(userId, exception.message ?: "Unknown sync error")
        throw exception
    }
}

suspend fun saveImportedTransaction(
    userId: UUID,
    account: StoredAccount,
    transaction: TransactionResponse
): Boolean {
    val displayMerchantName =
        transaction.merchantName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: transaction.description
                .trim()
                .takeIf { it.isNotBlank() }
            ?: "Unknown merchant"

    val logoLookupMerchantName = merchantNameForLogoLookup(transaction)

    val merchantLogoUrl = logoLookupMerchantName?.let {
        resolveMerchantLogoUrl(it)
    }
    val category = inferTransactionCategory(transaction)

    return Database.dataSource.connection.use { connection ->
        try {
            val wasInserted = connection.prepareStatement(
                """
                    INSERT INTO transactions (
                        user_id, connected_account_id, provider_account_id, provider_transaction_id,
                        merchant_name, description, category, account_name, amount, currency,
                        transaction_type, transaction_timestamp, merchant_logo_url, transaction_status
                    )
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    WHERE EXISTS (
                        SELECT 1
                        FROM connected_accounts
                        WHERE id = ?
                          AND user_id = ?
                          AND connection_status = 'connected'
                    )
                    ON CONFLICT (user_id, provider_account_id, provider_transaction_id)
                    DO UPDATE SET
                        merchant_name = EXCLUDED.merchant_name,
                        description = EXCLUDED.description,
                        account_name = EXCLUDED.account_name,
                        amount = EXCLUDED.amount,
                        currency = EXCLUDED.currency,
                        transaction_type = EXCLUDED.transaction_type,
                        transaction_timestamp = EXCLUDED.transaction_timestamp,
                        merchant_logo_url = EXCLUDED.merchant_logo_url,
                        transaction_status = EXCLUDED.transaction_status,
                        category = CASE
                            WHEN transactions.category_source = 'manual' THEN transactions.category
                            ELSE EXCLUDED.category
                        END
                    WHERE transactions.transaction_status IS DISTINCT FROM EXCLUDED.transaction_status
                       OR transactions.merchant_name IS DISTINCT FROM EXCLUDED.merchant_name
                       OR transactions.description IS DISTINCT FROM EXCLUDED.description
                       OR transactions.account_name IS DISTINCT FROM EXCLUDED.account_name
                       OR transactions.amount IS DISTINCT FROM EXCLUDED.amount
                       OR transactions.currency IS DISTINCT FROM EXCLUDED.currency
                       OR transactions.transaction_type IS DISTINCT FROM EXCLUDED.transaction_type
                       OR transactions.transaction_timestamp IS DISTINCT FROM EXCLUDED.transaction_timestamp
                       OR transactions.merchant_logo_url IS DISTINCT FROM EXCLUDED.merchant_logo_url
                       OR (
                           transactions.category_source <> 'manual'
                           AND transactions.category IS DISTINCT FROM EXCLUDED.category
                       )
                    RETURNING (xmax = 0) AS inserted
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, account.dbId)
                statement.setString(3, account.accountId)
                statement.setString(4, transaction.transactionId)
                statement.setString(5, displayMerchantName)
                statement.setString(6, transaction.description)
                statement.setString(7, category)
                statement.setString(8, account.accountName)
                statement.setDouble(9, transaction.amount)
                statement.setString(10, transaction.currency)
                statement.setString(11, transaction.type)
                statement.setTimestamp(12, Timestamp.from(Instant.parse(transaction.timestamp)))
                statement.setString(13, merchantLogoUrl)
                statement.setString(14, transaction.status)
                statement.setObject(15, account.dbId)
                statement.setObject(16, userId)
                statement.executeQuery().use { result ->
                    result.next() && result.getBoolean("inserted")
                }
            }

            connection.commit()
            wasInserted
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun deleteStalePendingTransactions(
    userId: UUID,
    account: StoredAccount,
    latestPendingTransactionIds: Set<String>
) {
    Database.dataSource.connection.use { connection ->
        try {
            val sql = if (latestPendingTransactionIds.isEmpty()) {
                """
                    DELETE FROM transactions
                    WHERE user_id = ?
                      AND provider_account_id = ?
                      AND transaction_status = 'pending'
                """.trimIndent()
            } else {
                """
                    DELETE FROM transactions
                    WHERE user_id = ?
                      AND provider_account_id = ?
                      AND transaction_status = 'pending'
                      AND provider_transaction_id <> ALL (?::text[])
                """.trimIndent()
            }

            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, account.accountId)

                if (latestPendingTransactionIds.isNotEmpty()) {
                    statement.setArray(
                        3,
                        connection.createArrayOf(
                            "text",
                            latestPendingTransactionIds.toTypedArray()
                        )
                    )
                }

                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

internal fun getImportedTransactionsForUser(
    userId: UUID,
    page: Int,
    pageSize: Int,
    type: String?
): PaginatedTransactionsResponse {
    val offset = page * pageSize
    val typeCondition = when (type?.lowercase()) {
        "income" -> "AND amount > 0"
        "expenses" -> "AND amount < 0"
        else -> ""
    }

    return Database.dataSource.connection.use { connection ->
        val totalCount = connection.prepareStatement(
            """
                    SELECT COUNT(*) FROM transactions WHERE user_id = ?
                    $typeCondition
                """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1)
            }
        }

        val transactions = connection.prepareStatement(
            """
                    SELECT id, transaction_timestamp, merchant_name, 
                        COALESCE(NULLIF(category, ''), 'Others') AS category, 
                        account_name, provider_account_id AS account_id,
                        amount, currency, merchant_logo_url,
                        COALESCE(transaction_status, 'settled') AS transaction_status
                    FROM transactions WHERE user_id = ?
                    $typeCondition
                    ORDER BY transaction_timestamp DESC, id DESC LIMIT ? OFFSET ?
                    """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setInt(2, pageSize)
            statement.setInt(3, offset)

            statement.executeQuery().use { result ->
                val items = mutableListOf<ImportedTransactionResponse>()

                while (result.next()) {
                    items.add(
                        ImportedTransactionResponse(
                            id = result.getObject("id").toString(),
                            date = result.getTimestamp("transaction_timestamp").toInstant()
                                .toString(),
                            merchantName = result.getString("merchant_name"),
                            accountId = result.getString("account_id"),
                            category = result.getString("category"),
                            accountName = result.getString("account_name"),
                            amount = result.getDouble("amount"),
                            currency = result.getString("currency"),
                            merchantLogoUrl = result.getString("merchant_logo_url"),
                            transactionStatus = result.getString("transaction_status")
                        )
                    )
                }

                items
            }
        }

        PaginatedTransactionsResponse(
            transactions = transactions,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount,
            hasMore = offset + transactions.size < totalCount
        )
    }
}

private fun recordTransactionSyncSuccess(userId: UUID, syncedAt: Instant) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO transaction_sync_status
                        (user_id, last_attempted_sync_at, last_successful_sync_at, last_error)
                    VALUES (?, ?, ?, NULL) ON CONFLICT (user_id)
                    DO UPDATE SET
                        last_attempted_sync_at = EXCLUDED.last_attempted_sync_at,
                        last_successful_sync_at = EXCLUDED.last_successful_sync_at,
                        last_error = NULL
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setTimestamp(2, Timestamp.from(syncedAt))
                statement.setTimestamp(3, Timestamp.from(syncedAt))
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun recordTransactionSyncFailure(userId: UUID, error: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO transaction_sync_status
                        (user_id, last_attempted_sync_at, last_error)
                    VALUES (?, now(), ?) ON CONFLICT (user_id)
                    DO UPDATE SET
                        last_attempted_sync_at = now(),
                        last_error = EXCLUDED.last_error
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, error)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun recordTransactionSyncPartialFailure(
    userId: UUID,
    attemptedAt: Instant,
    error: String
) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                INSERT INTO transaction_sync_status
                    (user_id, last_attempted_sync_at, last_error)
                VALUES (?, ?, ?) ON CONFLICT (user_id)
                DO UPDATE SET
                    last_attempted_sync_at = EXCLUDED.last_attempted_sync_at,
                    last_error = EXCLUDED.last_error
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setTimestamp(2, Timestamp.from(attemptedAt))
                statement.setString(3, error)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

internal fun getLastSuccessfulTransactionSync(userId: UUID): String? {
    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT last_successful_sync_at FROM transaction_sync_status WHERE user_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if(result.next()) {
                    result.getTimestamp("last_successful_sync_at")?.toInstant()?.toString()
                } else {
                    null
                }
            }
        }
    }
}
