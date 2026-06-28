package com.smart_finance_app.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0",
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    Database.connect()

    environment.monitor.subscribe(ApplicationStopped) {
        Database.close()
    }

    routing {
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
            call.respondText("OK")
        }
    }
}