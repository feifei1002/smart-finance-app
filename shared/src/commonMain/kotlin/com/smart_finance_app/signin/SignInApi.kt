package com.smart_finance_app.signin

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class SignInRequest(val email: String, val password: String)
@Serializable
data class AuthSession(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val email: String,
    val consentAccepted: Boolean,
    val language: String = "en"
)

sealed interface SignInResult {
    data class Success(val session: AuthSession): SignInResult
    data class Failure(val message: StringKey): SignInResult
}

class SignInApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

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
                    SignInResult.Failure(StringKey.AUTH_ERROR_INVALID_CREDENTIALS)
                }

                HttpStatusCode.TooManyRequests -> {
                    SignInResult.Failure(StringKey.AUTH_ERROR_TOO_MANY_ATTEMPTS)
                }
                
                else -> {
                    SignInResult.Failure(StringKey.COMMON_ERROR_UNKNOWN)
                }
            }
        } catch (_: Exception) {
            SignInResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}