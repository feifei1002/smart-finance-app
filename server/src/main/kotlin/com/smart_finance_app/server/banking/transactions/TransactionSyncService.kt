package com.smart_finance_app.server.banking.transactions

import com.smart_finance_app.server.Database
import com.smart_finance_app.server.banking.accounts.getStoredAccountsWithTokens
import com.smart_finance_app.server.banking.config.bankingLogger
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
import com.smart_finance_app.server.profile.userExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.use

private data class SaveTransactionsResult(
    val importedCount: Int,
    val duplicateCount: Int
)
private data class ExistingImportedTransaction(
    val category: String?,
    val merchantLogoUrl: String?
)

suspend fun syncTransactionsForUser(userId: UUID): TransactionSyncResponse = withContext(Dispatchers.IO) {
    if (!userExists(userId)) {
        error("User no longer exists")
    }

    var importedCount = 0
    var duplicateCount = 0
    var succeededAccounts = 0

    try {
        val storedAccounts = getStoredAccountsWithTokens(userId)
        val failedAccounts = mutableListOf<String>()

        storedAccounts.forEach { account ->
            runCatching {
                val token = ensureFreshToken(account)

//                val settledTransactions = fetchTransactions(token, account.accountId)

                val isInitialImport = account.initialTransactionsImportedAt == null
                val todayUtc = LocalDate.now(ZoneOffset.UTC)

                bankingLogger.info(
                    "Syncing account {} initialImport={}",
                    account.accountName,
                    isInitialImport
                )

                val settledTransactions = if (isInitialImport) {
                    bankingLogger.info("Fetching initial 6-month import for account {}", account.dbId)

                    fetchTransactions(
                        accessToken = token,
                        accountId = account.accountId,
                        from = todayUtc.minusMonths(6),
                        to = todayUtc
                    )
                } else {
                    bankingLogger.info("Fetching regular 89-day sync for account {}", account.dbId)

                    fetchTransactions(token, account.accountId)
                }

                val pendingFetchResult = runCatching {
                    fetchPendingTransactions(token, account.accountId)
                }

                val pendingTransactions = pendingFetchResult.getOrElse {
                    emptyList()
                }

                val transactions = settledTransactions + pendingTransactions

                val saveResult = saveImportedTransactionsForAccount(
                    userId = userId,
                    account = account,
                    transactions = transactions,
                    latestPendingTransactionIds = pendingFetchResult.getOrNull()
                        ?.map { it.transactionId }
                        ?.toSet()
                )

                if (isInitialImport) {
                    markInitialTransactionsImported(account.dbId)
                }

                importedCount += saveResult.importedCount
                duplicateCount += saveResult.duplicateCount
                succeededAccounts++
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

private suspend fun saveImportedTransactionsForAccount(
    userId: UUID,
    account: StoredAccount,
    transactions: List<TransactionResponse>,
    latestPendingTransactionIds: Set<String>?
): SaveTransactionsResult {
    var importedCount = 0
    var duplicateCount = 0
    val transactionIds = transactions.map { it.transactionId }.toSet()

    val knownTransactionIds = Database.dataSource.connection.use { connection ->
        try {
            loadExistingTransactionsForAccount(
                connection = connection,
                userId = userId,
                account = account,
                transactionIds = transactionIds
            ).keys
        } finally {
            connection.rollback()
        }
    }

    val newCategories = inferCategoriesWithTimeout(
        transactions.filter { it.transactionId !in knownTransactionIds }
    )

    return Database.dataSource.connection.use { connection ->
        try {
            val existingTransactions = loadExistingTransactionsForAccount(
                connection = connection,
                userId = userId,
                account = account,
                transactionIds = transactionIds
            )

            connection.prepareStatement(
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
                    merchant_logo_url = COALESCE(transactions.merchant_logo_url, EXCLUDED.merchant_logo_url),
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
                   OR (
                       transactions.merchant_logo_url IS NULL
                       AND EXCLUDED.merchant_logo_url IS NOT NULL
                   )
                   OR (
                       transactions.category_source <> 'manual'
                       AND transactions.category IS DISTINCT FROM EXCLUDED.category
                   )
                RETURNING (xmax = 0) AS inserted
                """.trimIndent()
            ).use { statement ->
                transactions.forEach { transaction ->
                    val inserted = upsertImportedTransaction(
                        statement = statement,
                        userId = userId,
                        account = account,
                        transaction = transaction,
                        existing = existingTransactions[transaction.transactionId],
                        newCategory = newCategories[transaction.transactionId]
                    )

                    if (inserted) importedCount++ else duplicateCount++
                }
            }

            if (latestPendingTransactionIds != null) {
                deleteStalePendingTransactions(
                    connection = connection,
                    userId = userId,
                    account = account,
                    latestPendingTransactionIds = latestPendingTransactionIds
                )
            }

            connection.commit()

            SaveTransactionsResult(importedCount, duplicateCount)
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private suspend fun upsertImportedTransaction(
    statement: PreparedStatement,
    userId: UUID,
    account: StoredAccount,
    transaction: TransactionResponse,
    existing: ExistingImportedTransaction?,
    newCategory: String?
): Boolean {
    val displayMerchantName =
        transaction.merchantName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: transaction.description
                .trim()
                .takeIf { it.isNotBlank() }
            ?: "Unknown merchant"

    val isNewTransaction = existing == null

    val merchantLogoUrl = if (isNewTransaction) {
        merchantNameForLogoLookup(transaction)?.let {
            resolveMerchantLogoUrl(it)
        }
    } else {
        existing.merchantLogoUrl
    }

    val category = if (isNewTransaction) {
        newCategory ?: inferTransactionCategoryFast(transaction)
    } else {
        existing.category ?: "Others"
    }

    statement.clearParameters()

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

    return statement.executeQuery().use { result ->
        result.next() && result.getBoolean("inserted")
    }
}


private fun deleteStalePendingTransactions(
    connection: Connection,
    userId: UUID,
    account: StoredAccount,
    latestPendingTransactionIds: Set<String>
) {
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

private fun markInitialTransactionsImported(accountDbId: UUID) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE connected_accounts
                SET initial_transactions_imported_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND connection_status = 'connected'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, accountDbId)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun loadExistingTransactionsForAccount(
    connection: Connection,
    userId: UUID,
    account: StoredAccount,
    transactionIds: Set<String>
): Map<String, ExistingImportedTransaction> {
    if (transactionIds.isEmpty()) {
        return emptyMap()
    }

    return connection.prepareStatement(
        """
        SELECT provider_transaction_id, category, merchant_logo_url
        FROM transactions
        WHERE user_id = ?
          AND provider_account_id = ?
          AND provider_transaction_id = ANY (?::text[])
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, userId)
        statement.setString(2, account.accountId)
        statement.setArray(
            3,
            connection.createArrayOf("text", transactionIds.toTypedArray())
        )

        statement.executeQuery().use { result ->
            val existing = mutableMapOf<String, ExistingImportedTransaction>()

            while (result.next()) {
                existing[result.getString("provider_transaction_id")] =
                    ExistingImportedTransaction(
                        category = result.getString("category")?.takeIf { it.isNotBlank() },
                        merchantLogoUrl = result.getString("merchant_logo_url")?.takeIf { it.isNotBlank() }
                    )
            }

            existing
        }
    }
}

private fun recordTransactionSyncSuccess(userId: UUID, syncedAt: Instant) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO transaction_sync_status
                        (user_id, last_attempted_sync_at, last_successful_sync_at, last_error)
                    SELECT ?, ?, ?, NULL WHERE EXISTS (SELECT 1 FROM users WHERE id = ?)
                    ON CONFLICT (user_id)
                    DO UPDATE SET
                        last_attempted_sync_at = EXCLUDED.last_attempted_sync_at,
                        last_successful_sync_at = EXCLUDED.last_successful_sync_at,
                        last_error = NULL
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setTimestamp(2, Timestamp.from(syncedAt))
                statement.setTimestamp(3, Timestamp.from(syncedAt))
                statement.setObject(4, userId)
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
                    SELECT ?, now(), ? WHERE EXISTS (SELECT 1 FROM users WHERE id = ?)
                    ON CONFLICT (user_id)
                    DO UPDATE SET
                        last_attempted_sync_at = now(),
                        last_error = EXCLUDED.last_error
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, error)
                statement.setObject(3, userId)
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
                SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM users WHERE id = ?)
                ON CONFLICT (user_id)
                DO UPDATE SET
                    last_attempted_sync_at = EXCLUDED.last_attempted_sync_at,
                    last_error = EXCLUDED.last_error
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setTimestamp(2, Timestamp.from(attemptedAt))
                statement.setString(3, error)
                statement.setObject(4, userId)
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

private suspend fun inferCategoriesWithTimeout(
    transactions: List<TransactionResponse>
): Map<String, String> {
    if (transactions.isEmpty()) return emptyMap()

    val categories = try {
        withTimeout(25_000.milliseconds) {
            inferTransactionCategories(transactions)
        }
    } catch (_: TimeoutCancellationException) {
        transactions.map(::inferTransactionCategoryFast)
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        transactions.map(::inferTransactionCategoryFast)
    }

    return transactions.zip(categories).associate { (transaction, category) ->
        transaction.transactionId to category
    }
}