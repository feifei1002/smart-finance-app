package com.smart_finance_app.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.*
import java.util.Date

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
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: error("Missing environment variable: JWT_SECRET")

    val jwtIssuer = "smart-finance-server"
    val jwtAudience = "smart-finance-app"
    val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Smart Finance"

            verifier(
                JWT.require(jwtAlgorithm)
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (!userId.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Token is invalid or expired")
                )
            }
        }
    }

    routing {
        registrationRoutes { userId ->
            JWT.create()
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .withClaim("userId", userId.toString())
                .withExpiresAt(
                    Date(
                        System.currentTimeMillis() +
                                15 * 60 * 1000L
                    )
                )
                .sign(jwtAlgorithm)
        }

        signInRoutes { userId ->
            JWT.create()
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .withClaim("userId", userId.toString())
                .withExpiresAt(
                    Date(
                        System.currentTimeMillis() +
                                15 * 60 * 1000L
                    )
                )
                .sign(jwtAlgorithm)
        }
        consentRoutes()

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