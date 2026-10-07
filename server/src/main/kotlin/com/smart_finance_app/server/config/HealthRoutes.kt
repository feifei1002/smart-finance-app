package com.smart_finance_app.server.config

import com.smart_finance_app.server.Database
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.use

internal fun Route.healthRoutes() {
    get("/") {
        call.respondText("Smart Finance backend is running")
    }

    get("/health/database") {
        Database.dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT current_database(), current_user"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    result.next()

                    call.respondText(
                        "Connected to ${result.getString(1)} " +
                                "as ${result.getString(2)}"
                    )
                }
            }
        }
    }
}