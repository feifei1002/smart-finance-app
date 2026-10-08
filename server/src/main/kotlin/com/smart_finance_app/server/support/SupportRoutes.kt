package com.smart_finance_app.server.support

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
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
import org.slf4j.LoggerFactory
import java.util.UUID

private val supportLogger = LoggerFactory.getLogger("SupportMessages")

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
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val request = runCatching { call.receive<SupportMessageRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                    return@post
                }

            // ── 1. Validation ─────────────────────────────────────────────────
            val message = validateSupportMessage(request).getOrElse { error ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error.message ?: "Invalid request"))
                return@post
            }

            // ── 2. Save as 'pending' before sending ───────────────────────────
            val messageId = UUID.randomUUID()

            val reserveResult = withContext(Dispatchers.IO) {
                runCatching {
                    reserveSupportMessage(
                        id = messageId,
                        userId = userId,
                        message = message
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
            val email = buildSupportEmail(
                messageId = messageId,
                userId = userId,
                user = user,
                message = message
            )

            val sendResult = withContext(Dispatchers.IO) {
                runCatching {
                    SupportMailer.send(
                        subject = email.subject,
                        body = email.body,
                        replyTo = email.replyTo
                    )
                }
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