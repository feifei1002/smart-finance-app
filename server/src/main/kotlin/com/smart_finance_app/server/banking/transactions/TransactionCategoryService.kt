package com.smart_finance_app.server.banking.transactions

import com.smart_finance_app.server.CategoryServiceClient
import com.smart_finance_app.server.Database
import com.smart_finance_app.server.banking.models.TransactionResponse
import java.util.UUID
import kotlin.use

internal val allowedTransactionCategories = setOf(
    "Food & Dining",
    "Shopping & Personal",
    "Bills & Housing",
    "Entertainment & Subscriptions",
    "Transportation",
    "Transfers",
    "Income",
    "Others"
)

suspend fun inferTransactionCategory(transaction: TransactionResponse): String {
    val rawDescription = "${transaction.merchantName.orEmpty()} ${transaction.description}".trim()
    val cleanDesc = rawDescription.split(Regex("[#\\-(]")).first().trim()

    if (cleanDesc.isEmpty()) return "Others"

    val isPersonTransfer =
        cleanDesc.contains(Regex("\\b(MR|MS|MRS|MISS|DR)\\s+[A-Z]", RegexOption.IGNORE_CASE)) ||
                cleanDesc.contains("FASTER PAYMENT", ignoreCase = true) ||
                cleanDesc.contains("BANK TRANSFER", ignoreCase = true) ||
                cleanDesc.contains("TRANSFER", ignoreCase = true)

    if (isPersonTransfer) {
        return "Transfers"
    }

    if (cleanDesc.contains("REFUND", ignoreCase = true)) {
        return CategoryServiceClient.classify(cleanDesc)
    }

    if (transaction.amount > 0 || transaction.type.equals("CREDIT", ignoreCase = true)) {
        return "Income"
    }

    return CategoryServiceClient.classify(cleanDesc)
}

internal fun updateTransactionCategoryForUser(
    userId: UUID,
    transactionId: UUID,
    category: String
): Boolean {
    return Database.dataSource.connection.use { connection ->
        try {
            val updated = connection.prepareStatement(
                """
                UPDATE transactions
                SET category = ?, category_source = 'manual'
                WHERE id = ? AND user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, category)
                statement.setObject(2, transactionId)
                statement.setObject(3, userId)
                statement.executeUpdate()
            }

            connection.commit()
            updated > 0
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

internal fun getTransactionAmountForUser(
    userId: UUID,
    transactionId: UUID
): Double? {
    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT amount
            FROM transactions
            WHERE id = ? AND user_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, transactionId)
            statement.setObject(2, userId)

            statement.executeQuery().use { result ->
                if (result.next()) result.getDouble("amount") else null
            }
        }
    }
}

internal suspend fun recategorizeTransactionsForUser(userId: UUID): Int {
    val transactions = Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, provider_transaction_id, transaction_timestamp, description,
                   amount, currency, transaction_type, merchant_name
            FROM transactions
            WHERE user_id = ? AND COALESCE(category_source, 'auto') <> 'manual'
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                val items = mutableListOf<Pair<UUID, TransactionResponse>>()

                while (result.next()) {
                    items.add(
                        result.getObject("id", UUID::class.java) to TransactionResponse(
                            transactionId = result.getString("provider_transaction_id"),
                            timestamp = result.getTimestamp("transaction_timestamp").toInstant()
                                .toString(),
                            description = result.getString("description"),
                            amount = result.getDouble("amount"),
                            currency = result.getString("currency"),
                            type = result.getString("transaction_type"),
                            merchantName = result.getString("merchant_name")
                        )
                    )
                }

                items
            }
        }
    }

    var updatedCount = 0

    transactions.forEach { (id, transaction) ->
        val category = inferTransactionCategory(transaction)

        Database.dataSource.connection.use { connection ->
            try {
                val updated = connection.prepareStatement(
                    """
                    UPDATE transactions
                    SET category = ?, category_source = 'auto'
                    WHERE id = ? AND COALESCE(category_source, 'auto') <> 'manual'
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, category)
                    statement.setObject(2, id)
                    statement.executeUpdate()
                }

                connection.commit()
                updatedCount += updated
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
    }

    return updatedCount
}
