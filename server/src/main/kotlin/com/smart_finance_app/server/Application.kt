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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

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

    System.getenv("ENCRYPTION_KEY")
        ?: error("Missing environment variable: ENCRYPTION_KEY")


    val jwtIssuer = "smart-finance-server"
    val jwtAudience = "smart-finance-app"
    val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

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

        registrationRoutes(
            createAccessToken = { userId -> createJwtToken(userId, jwtIssuer, jwtAudience, jwtAlgorithm) },
            createRefreshToken = { userId -> createRefreshToken(userId) }
        )

        signInRoutes(
            createAccessToken = { userId -> createJwtToken(userId, jwtIssuer, jwtAudience, jwtAlgorithm) },
            createRefreshToken = { userId -> createRefreshToken(userId) }
        )

        sessionRoutes(
            createAccessToken = { userId -> createJwtToken(userId, jwtIssuer, jwtAudience, jwtAlgorithm) }
        )

        passwordResetRoutes()
        consentRoutes()
        bankingRoutes()
        budgetRoutes()

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

        subscriptionRoutes()
    }
}

private fun createJwtToken(
    userId: UUID,
    issuer: String,
    audience: String,
    algorithm: Algorithm
): String {
    return JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId.toString())
        .withExpiresAt(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
        .sign(algorithm)
}