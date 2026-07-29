package com.smart_finance_app.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import java.util.UUID

// ── Request / Response models ─────────────────────────────────────────────────

@Serializable
data class BudgetRequest(
    val category: String,
    val amount: Double,
    val period: String   // "monthly" or "weekly"
)

@Serializable
data class BudgetResponse(
    val id: String,
    val category: String,
    val amount: Double,
    val period: String,
    val createdAt: String
)

// ── Routes ────────────────────────────────────────────────────────────────────

fun Route.budgetRoutes() {
    authenticate("auth-jwt") {

        /**
         * GET /api/budgets
         * Returns all budgets for the authenticated user.
         */
        get("/api/budgets") {
            val userId = getUserId(call.principal()) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@get
            }

            val budgets = getBudgetsForUser(userId)
            call.respond(budgets)
        }

        /**
         * POST /api/budgets
         * Creates a new budget. Returns 409 if category+period already exists.
         */
        post("/api/budgets") {
            val userId = getUserId(call.principal()) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@post
            }

            val request = runCatching { call.receive<BudgetRequest>() }.getOrElse { err ->
                println("❌ JSON DESERIALIZATION ERROR: ${err.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${err.message}"))
                return@post
            }

            println("📥 RECEIVED BUDGET REQUEST: $request for userId: $userId")

            try {
                val budget = createBudget(userId, request)
                call.respond(HttpStatusCode.Created, budget)
            } catch (e: Exception) {
                // Print the real error to your IDE Terminal/Console:
                println("❌ CREATE BUDGET DATABASE ERROR:")
                e.printStackTrace()

                if (e.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("duplicate key", ignoreCase = true) == true
                ) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("A ${request.period} budget for ${request.category} already exists")
                    )
                } else {
                    // Return the actual exception message back to the UI!
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("DB Error: ${e.message ?: e.javaClass.simpleName}")
                    )
                }
            }
        }
        /**
         * PUT /api/budgets/{id}
         * Updates the amount of an existing budget.
         */
        put("/api/budgets/{id}") {
            val userId   = getUserId(call.principal()) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@put
            }
            val budgetId = call.parameters["id"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid budget ID"))
                return@put
            }

            val request = runCatching { call.receive<BudgetRequest>() }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                return@put
            }

            if (request.category.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Category is required"))
                return@put
            }

            if (request.amount <= 0) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Amount must be greater than 0"))
                return@put
            }

            if (request.period !in listOf("monthly", "weekly")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Period must be monthly or weekly"))
                return@put
            }

            try {
                val updated = updateBudget(userId, budgetId, request)
                if (!updated) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Budget not found"))
                    return@put
                }
                call.respond(HttpStatusCode.OK, ErrorResponse("Budget updated"))
            } catch (e: Exception) {
                if (
                    e.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("duplicate key", ignoreCase = true) == true
                ) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("A ${request.period} budget for ${request.category} already exists")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("DB Error: ${e.message ?: e.javaClass.simpleName}")
                    )
                }
            }
        }

        /**
         * DELETE /api/budgets/{id}
         * Deletes a budget. Only the owner can delete their budget.
         */
        delete("/api/budgets/{id}") {
            val userId   = getUserId(call.principal()) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@delete
            }
            val budgetId = call.parameters["id"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid budget ID"))
                return@delete
            }

            val deleted = deleteBudget(userId, budgetId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Budget not found"))
                return@delete
            }

            call.respond(HttpStatusCode.OK, ErrorResponse("Budget deleted"))
        }
    }
}

// ── Helper ────────────────────────────────────────────────────────────────────

private fun getUserId(principal: JWTPrincipal?): UUID? =
    principal?.payload?.getClaim("userId")?.asString()
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

// ── Database operations ───────────────────────────────────────────────────────

private fun getBudgetsForUser(userId: UUID): List<BudgetResponse> =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, category, amount, period, created_at
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
                            id         = result.getObject("id").toString(),
                            category   = result.getString("category"),
                            amount     = result.getDouble("amount"),
                            period     = result.getString("period"),
                            createdAt  = result.getTimestamp("created_at").toString()
                        )
                    )
                }
                list
            }
        }
    }

private fun createBudget(userId: UUID, request: BudgetRequest): BudgetResponse =
    Database.dataSource.connection.use { connection ->
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false

            // Explicitly cast user_id to UUID and use gen_random_uuid() / NOW() if defaults are missing
            val result = connection.prepareStatement(
                """
                INSERT INTO budgets (id, user_id, category, amount, period, created_at)
                VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, NOW())
                RETURNING id, category, amount, period, created_at
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, userId.toString()) // Set string + cast in SQL for maximum driver compatibility
                statement.setString(2, request.category)
                statement.setDouble(3, request.amount)
                statement.setString(4, request.period)

                statement.executeQuery().use { rs ->
                    check(rs.next()) { "Failed to insert row" }
                    BudgetResponse(
                        id        = rs.getObject("id").toString(),
                        category  = rs.getString("category"),
                        amount    = rs.getDouble("amount"),
                        period    = rs.getString("period"),
                        createdAt = rs.getTimestamp("created_at").toString()
                    )
                }
            }

            connection.commit()
            result
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            println("DATABASE ERROR IN CREATE_BUDGET: ${e.message}") // Log actual error to console
            e.printStackTrace()
            throw e
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
        }
    }

private fun updateBudget(userId: UUID, budgetId: UUID, request: BudgetRequest): Boolean =
    Database.dataSource.connection.use { connection ->
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val rows = connection.prepareStatement(
                """
                UPDATE budgets
                SET category = ?, amount = ?, period = ?, updated_at = NOW()
                WHERE id = ?::uuid AND user_id = ?::uuid
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, request.category)
                statement.setDouble(2, request.amount)
                statement.setString(3, request.period)
                statement.setString(4, budgetId.toString())
                statement.setString(5, userId.toString())
                statement.executeUpdate()
            }
            connection.commit()
            rows > 0
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            println("DATABASE ERROR IN UPDATE_BUDGET: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
        }
    }

private fun deleteBudget(userId: UUID, budgetId: UUID): Boolean =
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
            println("DATABASE ERROR IN DELETE_BUDGET: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
        }
    }