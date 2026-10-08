package com.smart_finance_app.server.config

import com.smart_finance_app.server.REFRESH_TOKEN_TRANSPORT_HEADER
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS

internal fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowCredentials = true
        allowHeader(REFRESH_TOKEN_TRANSPORT_HEADER)

        allowHost("localhost:8081", schemes = listOf("http"))
        allowHost("127.0.0.1:8081", schemes = listOf("http"))
        allowHost("192.168.1.246:8081", schemes = listOf("http"))

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
}