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

/** Result of saving a new message as 'pending' before it is emailed. */
private sealed interface ReserveResult {
    data class Reserved(val user: SupportUser) : ReserveResult
    data object RateLimited : ReserveResult
    data object UserNotFound : ReserveResult
}

fun Route.supportRoutes() {
    authenticate("auth-jwt") {

        /**
         * POST /api/support/messages
         * Sends a feedback or support message from the authenticated user to the project inbox.
         *
         * Order of operations:
         *   1. Validate the request.
         *   2. Save the message as 'pending' (rate limit is checked in the same transaction).
         *      If this fails, nothing is emailed.
         *   3. Send the email.
         *   4. Mark the message 'sent' or 'failed'.
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

            // ── 1. Validation ─────────────────────────────────────────────────
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

            // ── 2. Save as 'pending' before sending ───────────────────────────
            val messageId = UUID.randomUUID()

            val reserveResult = withContext(Dispatchers.IO) {
                runCatching {
                    reserveSupportMessage(
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
                }
            }.getOrElse { error ->
                // Nothing has been emailed, so it is safe to tell the user to retry.
                supportLogger.error("Could not save support message $messageId", error)
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Could not send your message. Please try again later.")
                )
                return@post
            }

            val user = when (reserveResult) {
                is ReserveResult.Reserved -> reserveResult.user
                ReserveResult.RateLimited -> {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("Too many messages. Please try again later.")
                    )
                    return@post
                }
                ReserveResult.UserNotFound -> {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                    return@post
                }
            }

            // ── 3. Send the email ─────────────────────────────────────────────
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

            // ── 4. Record the outcome ─────────────────────────────────────────
            val finalStatus = if (sendResult.isSuccess) "sent" else "failed"
            withContext(Dispatchers.IO) {
                runCatching { updateSupportMessageStatus(messageId, finalStatus) }
            }.onFailure { error ->
                // The row stays 'pending', which still counts towards the hourly limit,
                // so the limit can't be bypassed even if this update fails.
                supportLogger.error("Could not mark support message $messageId as $finalStatus", error)
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

            call.respond(HttpStatusCode.Created, SupportMessageResponse(messageId.toString()))
        }
    }
}

private fun String?.cleanMeta(): String? =
    this?.trim()
        ?.replace(Regex("[\\r\\n]+"), " ")
        ?.take(MAX_META_LENGTH)
        ?.takeIf { it.isNotEmpty() }

/**
 * In one transaction: locks the user's row, checks the hourly limit, and inserts the
 * message as 'pending'. Locking the user's row means two requests from the same user
 * at the same moment are counted one after the other, so both can't slip past the limit.
 *
 * Only 'pending' and 'sent' messages count towards the limit. 'failed' ones never reached
 * the inbox, so users aren't locked out because of an email outage on our side.
 */
private fun reserveSupportMessage(
    id: UUID,
    userId: UUID,
    type: String,
    rating: Int?,
    category: String?,
    message: String,
    platform: String?,
    appVersion: String?,
    language: String?
): ReserveResult =
    Database.dataSource.connection.use { connection ->
        try {
            val user = connection.prepareStatement(
                "SELECT email, full_name FROM users WHERE id = ? FOR UPDATE"
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

            if (user == null) {
                connection.rollback()
                return@use ReserveResult.UserNotFound
            }

            val recentCount = connection.prepareStatement(
                """
                    SELECT count(*)
                    FROM support_messages
                    WHERE user_id = ?
                      AND status IN ('pending', 'sent')
                      AND created_at > now() - interval '1 hour'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getInt(1) else 0
                }
            }

            if (recentCount >= MAX_MESSAGES_PER_HOUR) {
                connection.rollback()
                return@use ReserveResult.RateLimited
            }

            connection.prepareStatement(
                """
                    INSERT INTO support_messages (
                        id, user_id, type, rating, category, message,
                        platform, app_version, language, status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')
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
            ReserveResult.Reserved(user)
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

private fun updateSupportMessageStatus(id: UUID, status: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                "UPDATE support_messages SET status = ? WHERE id = ?"
            ).use { statement ->
                statement.setString(1, status)
                statement.setObject(2, id)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}