package com.smart_finance_app.server.budget

import com.smart_finance_app.server.Database
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.use

private val budgetLogger = LoggerFactory.getLogger("BudgetRepository")
internal fun getBudgetsForUser(userId: UUID): List<BudgetResponse> =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
        SELECT id, category, amount, period, currency, created_at
        FROM budgets
        WHERE user_id = ?
        ORDER BY created_at ASC
        """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                val list = mutableListOf<BudgetResponse>()
                while (result.next()) {
                    list.add(
                        BudgetResponse(
                            id = result.getObject("id").toString(),
                            category = result.getString("category"),
                            amount = result.getDouble("amount"),
                            period = result.getString("period"),
                            currency = result.getString("currency") ?: "GBP",  // ← add
                            createdAt = result.getTimestamp("created_at").toString()
                        )
                    )
                }
                list
            }
        }
    }

internal fun createBudget(userId: UUID, request: BudgetRequest): BudgetResponse =
    Database.dataSource.connection.use { connection ->
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false

            val result = connection.prepareStatement(
                """
                INSERT INTO budgets (id, user_id, category, amount, period, currency, created_at)
                VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, NOW())
                RETURNING id, category, amount, period, currency, created_at
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, userId.toString())
                statement.setString(2, request.category)
                statement.setDouble(3, request.amount)
                statement.setString(4, request.period)
                statement.setString(5, request.currency)  // ← add

                statement.executeQuery().use { rs ->
                    check(rs.next()) { "Failed to insert row" }
                    BudgetResponse(
                        id        = rs.getObject("id").toString(),
                        category  = rs.getString("category"),
                        amount    = rs.getDouble("amount"),
                        period    = rs.getString("period"),
                        currency  = rs.getString("currency"),  // ← add
                        createdAt = rs.getTimestamp("created_at").toString()
                    )
                }
            }

            connection.commit()
            result
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            budgetLogger.error("Database error while creating budget", e)
            throw e
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
        }
    }

internal fun updateBudget(userId: UUID, budgetId: UUID, request: BudgetRequest): Boolean =
    Database.dataSource.connection.use { connection ->
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val rows = connection.prepareStatement(
                """
                UPDATE budgets
                SET category = ?, amount = ?, period = ?, currency = ?, updated_at = NOW()
                WHERE id = ?::uuid AND user_id = ?::uuid
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, request.category)
                statement.setDouble(2, request.amount)
                statement.setString(3, request.period)
                statement.setString(4, request.currency)  // ← add
                statement.setString(5, budgetId.toString())
                statement.setString(6, userId.toString())
                statement.executeUpdate()
            }
            connection.commit()
            rows > 0
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            budgetLogger.error("Database error while updating budget", e)
            throw e
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
        }
    }

internal fun deleteBudget(userId: UUID, budgetId: UUID): Boolean =
    Database.dataSource.connection.use { connection ->
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val rows = connection.prepareStatement(
                """
                DELETE FROM budgets
                WHERE id = ?::uuid AND user_id = ?::uuid
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, budgetId.toString())
                statement.setString(2, userId.toString())
                statement.executeUpdate()
            }
            connection.commit()
            rows > 0
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            budgetLogger.error("Database error while deleting budget", e)
            throw e
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
        }
    }