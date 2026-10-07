package com.smart_finance_app.server.config

import com.smart_finance_app.server.Database
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.use

internal fun Route.healthRoutes() {
    get("/") {
        call.respondText("Smart Finance backend is running")
    }

    get("/health/database") {
        val healthy = runCatching {
            Database.dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT 1").use { statement ->
                    statement.executeQuery().use { result ->
                        result.next()
                    }
                }
            }
        }.onFailure { exception ->
            call.application.environment.log.error("Database health check failed", exception)
        }.isSuccess

        if (healthy) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("status" to "unavailable")
            )
        }
    }
}