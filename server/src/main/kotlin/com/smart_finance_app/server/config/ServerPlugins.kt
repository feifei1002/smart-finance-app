package com.smart_finance_app.server.config

import com.smart_finance_app.server.REFRESH_TOKEN_TRANSPORT_HEADER
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

internal fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowCredentials = true
        allowHeader(REFRESH_TOKEN_TRANSPORT_HEADER)
        allowHeader("Idempotency-Key")

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

    install(RateLimit) {
        for ((name, limit) in listOf(
            "accounts-read" to 60,
            "accounts-write" to 10,
            "transactions-read" to 120,
            "transactions-write" to 30
        )) {
            register(RateLimitName(name)) {
                rateLimiter(limit = limit, refillPeriod = 60.seconds)
                requestKey { call ->
                    requireNotNull(call.principal<JWTPrincipal>()?.userIdOrNull())
                }
            }
        }
    }
}