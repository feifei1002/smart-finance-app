package com.smart_finance_app.registration

import com.smart_finance_app.signin.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
    val refreshToken: String,
    val userId: String,
    val name: String,
    val email: String,
    val consentAccepted: Boolean
)

@Serializable
private data class ErrorResponse(val message: String)

sealed interface RegistrationResult {
    data class Success(val session: AuthSession) : RegistrationResult
    data class Failure(val message: String) : RegistrationResult
}

class RegistrationApi(private val baseUrl: String, private val client: HttpClient) {

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
                            refreshToken = body.refreshToken,
                            userId = body.userId,
                            name = body.name,
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
        } catch (_: Exception) {
            RegistrationResult.Failure("Cannot connect to the server")
        }
    }
}