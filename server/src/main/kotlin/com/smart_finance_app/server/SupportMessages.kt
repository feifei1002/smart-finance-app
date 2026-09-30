package com.smart_finance_app.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

private const val MAX_MESSAGE_LENGTH = 2000
private const val MAX_MESSAGES_PER_HOUR = 5
private const val MAX_META_LENGTH = 32

private val allowedMessageTypes = setOf("feedback", "support")
private val allowedFeedbackCategories = setOf("bug", "idea", "other")

private val supportLogger = LoggerFactory.getLogger("SupportMessages")

@Serializable
data class SupportMessageRequest(
    val type: String,                 // "feedback" or "support"
    val message: String = "",
    val rating: Int? = null,          // feedback only, 1–5
    val category: String? = null,     // feedback only: "bug", "idea", "other"
    val platform: String? = null,     // e.g. "android", "ios", "web", "desktop"
    val appVersion: String? = null,
    val language: String? = null
)

@Serializable
data class SupportMessageResponse(val id: String)

private data class SupportUser(val email: String, val fullName: String)

fun Route.supportRoutes() {
    authenticate("auth-jwt") {

        /**
         * POST /api/support/messages
         * Sends a feedback or support message from the authenticated user to the project inbox.
         */
        post("/api/support/messages") {
            val userId = call.principal<JWTPrincipal>()
                ?.payload?.getClaim("userId")?.asString()
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@post
                }

            val request = runCatching { call.receive<SupportMessageRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                    return@post
                }

            // ── Validation ────────────────────────────────────────────────────
            val type = request.type.trim().lowercase()
            if (type !in allowedMessageTypes) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid message type"))
                return@post
            }

            val message = request.message.trim()
            if (message.length > MAX_MESSAGE_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Message is too long"))
                return@post
            }
            if (type == "support" && message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Message is required"))
                return@post
            }
            if (type == "feedback" && request.rating == null && message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Rating or message is required"))
                return@post
            }

            val rating = if (type == "feedback") request.rating else null
            if (rating != null && rating !in 1..5) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Rating must be between 1 and 5"))
                return@post
            }

            val category = if (type == "feedback") {
                request.category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            } else null
            if (category != null && category !in allowedFeedbackCategories) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid feedback category"))
                return@post
            }

            val platform = request.platform.cleanMeta()
            val appVersion = request.appVersion.cleanMeta()
            val language = request.language.cleanMeta()

            // ── Rate limit: max N messages per user per hour ──────────────────
            if (countRecentSupportMessages(userId) >= MAX_MESSAGES_PER_HOUR) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("Too many messages. Please try again later.")
                )
                return@post
            }

            val user = findSupportUser(userId) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                return@post
            }

            // ── Send the email ────────────────────────────────────────────────
            val messageId = UUID.randomUUID()
            val shortId = messageId.toString().take(8)
            val safeName = user.fullName.replace(Regex("[\\r\\n]+"), " ").trim().ifBlank { "User" }

            val subject = when (type) {
                "support" -> "[Support] $safeName (#$shortId)"
                else -> "[Feedback] ${rating?.let { "$it/5" } ?: "No rating"} · ${category ?: "general"} (#$shortId)"
            }

            val body = buildString {
                appendLine("Type: ${type.replaceFirstChar { it.uppercase() }}")
                appendLine("From: $safeName <${user.email}>")
                appendLine("User ID: $userId")
                appendLine("Message ID: $messageId")
                if (type == "feedback") {
                    appendLine("Rating: ${rating?.let { "$it/5" } ?: "—"}")
                    appendLine("Category: ${category ?: "—"}")
                }
                appendLine("Platform: ${platform ?: "unknown"}")
                appendLine("App version: ${appVersion ?: "unknown"}")
                appendLine("Language: ${language ?: "unknown"}")
                appendLine()
                appendLine("Message:")
                appendLine(message.ifBlank { "(no message)" })
                appendLine()
                appendLine("— Reply to this email to respond to the user directly.")
            }

            val sendResult = withContext(Dispatchers.IO) {
                runCatching { SupportMailer.send(subject, body, replyTo = user.email) }
            }
            sendResult.onFailure { error ->
                // Log the id and the error only — never the user's message text.
                supportLogger.error("Failed to send support email $messageId", error)
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Could not send your message. Please try again later.")
                )
                return@post
            }

            // ── Keep a copy in the database ───────────────────────────────────
            // The email already went out, so a failure here is logged but not shown to the user.
            runCatching {
                saveSupportMessage(
                    id = messageId,
                    userId = userId,
                    type = type,
                    rating = rating,
                    category = category,
                    message = message,
                    platform = platform,
                    appVersion = appVersion,
                    language = language
                )
            }.onFailure { error ->
                supportLogger.error("Support email $messageId sent but not saved to database", error)
            }

            call.respond(HttpStatusCode.Created, SupportMessageResponse(messageId.toString()))
        }
    }
}

private fun String?.cleanMeta(): String? =
    this?.trim()
        ?.replace(Regex("[\\r\\n]+"), " ")
        ?.take(MAX_META_LENGTH)
        ?.takeIf { it.isNotEmpty() }

private fun countRecentSupportMessages(userId: UUID): Int =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT count(*)
                FROM support_messages
                WHERE user_id = ?
                  AND created_at > now() - interval '1 hour'
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (result.next()) result.getInt(1) else 0
            }
        }
    }

// NOTE: check these column names match your users table (see Registration.kt / Profile.kt).
private fun findSupportUser(userId: UUID): SupportUser? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT email, full_name FROM users WHERE id = ?"
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (result.next()) {
                    SupportUser(
                        email = result.getString("email"),
                        fullName = result.getString("full_name") ?: ""
                    )
                } else null
            }
        }
    }

private fun saveSupportMessage(
    id: UUID,
    userId: UUID,
    type: String,
    rating: Int?,
    category: String?,
    message: String,
    platform: String?,
    appVersion: String?,
    language: String?
) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO support_messages (
                        id, user_id, type, rating, category, message, platform, app_version, language
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, userId)
                statement.setString(3, type)
                if (rating != null) statement.setShort(4, rating.toShort())
                else statement.setNull(4, java.sql.Types.SMALLINT)
                statement.setString(5, category)
                statement.setString(6, message)
                statement.setString(7, platform)
                statement.setString(8, appVersion)
                statement.setString(9, language)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}