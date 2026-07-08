package com.smart_finance_app.signin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SignInRequest(val email: String, val password: String)
@Serializable
data class AuthSession(val token: String, val userId: String, val email: String, val consentAccepted: Boolean)

@Serializable
private data class ErrorResponse(val message: String)

sealed interface SignInResult {
    data class Success(val session: AuthSession): SignInResult
    data class Failure(val message: String): SignInResult
}

class SignInApi(baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json { ignoreUnknownKeys = true }
            )
        }
    }

    suspend fun signIn(form: SignInForm): SignInResult {
        return try {
            val response = client.post(
                "$normalizedBaseUrl/auth/signin"
            ) {
                contentType(ContentType.Application.Json)
                setBody(
                    SignInRequest(
                        email = form.email.trim().lowercase(),
                        password = form.password
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    SignInResult.Success(response.body<AuthSession>())
                }

                HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized -> {
                    SignInResult.Failure(response.errorMessage("Invalid email or password"))
                }
                else -> {
                    SignInResult.Failure(response.errorMessage("Sign in failed (${response.status.value})"))
                }
            }
        } catch (exception: Exception) {
            SignInResult.Failure("Cannot connect to the server")
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun HttpResponse.errorMessage(fallback: String): String {
        return try {
            body<ErrorResponse>().message
        } catch (_: Exception) {
            fallback
        }
    }
}