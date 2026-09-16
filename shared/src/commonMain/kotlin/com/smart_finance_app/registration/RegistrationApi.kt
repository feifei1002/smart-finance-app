package com.smart_finance_app.registration

import com.smart_finance_app.StringKey
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
    val consentAccepted: Boolean,
    val language: String = "en"
)

@Serializable
private data class ErrorResponse(val message: String)

sealed interface RegistrationResult {
    data class Success(val session: AuthSession) : RegistrationResult
    data class Failure(val message: StringKey) : RegistrationResult
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
                            consentAccepted = body.consentAccepted,
                            language = body.language
                        )
                    )
                }
                HttpStatusCode.BadRequest -> {
                    RegistrationResult.Failure(StringKey.COMMON_ERROR_INVALID_REQUEST)
                }
                
                HttpStatusCode.Conflict -> RegistrationResult.Failure(StringKey.REGISTER_ERROR_EMAIL_EXISTS)
                else -> RegistrationResult.Failure(StringKey.COMMON_ERROR_UNKNOWN)
            }
        } catch (_: Exception) {
            RegistrationResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}