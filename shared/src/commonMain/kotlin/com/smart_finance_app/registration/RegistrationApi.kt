package com.smart_finance_app.registration

import com.smart_finance_app.signin.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

@Serializable
private data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String
)

@Serializable
data class RegisterResponse(
    val token: String,
    val userId: String,
    val email: String,
    val consentAccepted: Boolean
)

@Serializable
private data class ErrorResponse(val message: String)

sealed interface RegistrationResult {
    data class Success(val session: AuthSession) : RegistrationResult
    data class Failure(val message: String) : RegistrationResult
}

class RegistrationApi(private val baseUrl: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun register(form: RegistrationForm): RegistrationResult {
        return try {
            val response = client.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(fullName = form.fullName, email = form.email, password = form.password)
                )
            }

            when (response.status) {
                HttpStatusCode.Created -> {
                    val body = response.body<RegisterResponse>()

                    RegistrationResult.Success(
                        AuthSession(
                            token = body.token,
                            userId = body.userId,
                            email = body.email,
                            consentAccepted = body.consentAccepted
                        )
                    )
                }
                HttpStatusCode.BadRequest,
                HttpStatusCode.Conflict -> RegistrationResult.Failure(response.body<ErrorResponse>().message)
                else -> RegistrationResult.Failure(
                    "Registration failed (${response.status.value})"
                )
            }
        } catch (exception: Exception) {
            RegistrationResult.Failure("Cannot connect to the server")
        }
    }
}