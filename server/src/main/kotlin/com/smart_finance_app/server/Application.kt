package com.smart_finance_app.server

import com.smart_finance_app.server.config.configureAuthentication
import com.smart_finance_app.server.config.configurePlugins
import com.smart_finance_app.server.config.configureRoutes
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

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

    monitor.subscribe(ApplicationStopped) {
        Database.close()
    }

    configurePlugins()
    configureAuthentication()
    configureRoutes()
}