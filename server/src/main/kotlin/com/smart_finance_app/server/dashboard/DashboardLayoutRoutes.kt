package com.smart_finance_app.server.dashboard

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

internal fun Route.dashboardLayoutRoutes() {
    /**
     * GET /api/dashboard/layout
     *
     * Returns the saved dashboard layout for the authenticated user, or 404 if
     * they have never saved one (client should use its local defaults).
     */
    get("/api/dashboard/layout") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val layout = getDashboardLayout(userId)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No layout saved"))

        call.respond(layout)
    }

    /**
     * PUT /api/dashboard/layout
     *
     * Saves (upserts) the dashboard layout for the authenticated user.
     * Called by the client when the user presses Done in customise mode.
     */
    put("/api/dashboard/layout") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

        val body = runCatching { call.receive<DashboardLayoutRequest>() }
            .getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                return@put
            }
        val cleaned = cleanDashboardLayout(body)

        saveDashboardLayout(userId, cleaned)
        call.respond(HttpStatusCode.OK, mapOf("status" to "saved"))
    }
}