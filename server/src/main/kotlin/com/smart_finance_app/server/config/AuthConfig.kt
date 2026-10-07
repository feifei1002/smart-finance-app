package com.smart_finance_app.server.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.profile.userExists
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

internal const val jwtIssuer = "smart-finance-server"
internal const val jwtAudience = "smart-finance-app"

internal fun Application.configureAuthentication() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: error("Missing environment variable: JWT_SECRET")

    System.getenv("ENCRYPTION_KEY")
        ?: error("Missing environment variable: ENCRYPTION_KEY")

    val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

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
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@validate null

                // Reject tokens belonging to accounts that have been deleted
                val exists = withContext(Dispatchers.IO) { userExists(userId) }
                if (exists) JWTPrincipal(credential.payload) else null
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Token is invalid or expired")
                )
            }
        }
    }
}

internal fun createJwtToken(
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