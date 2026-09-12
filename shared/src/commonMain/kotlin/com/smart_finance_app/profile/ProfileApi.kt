package com.smart_finance_app.profile

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val fullName: String,
    val email: String
)

@Serializable
private data class UpdateProfileRequest(
    val fullName: String,
    val email: String
)

@Serializable
private data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
private data class ErrorResponse(
    val message: String
)

sealed interface ProfileResult {
    data class Success(val profile: ProfileResponse) : ProfileResult
    data class Failure(val message: String) : ProfileResult
}

sealed interface UpdateProfileResult {
    data class Success(val profile: ProfileResponse) : UpdateProfileResult
    data class Failure(val message: String) : UpdateProfileResult
}

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data class Failure(val message: String) : ChangePasswordResult
}

class ProfileApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun updateProfile(
        token: String,
        fullName: String,
        email: String
    ): UpdateProfileResult {
        return try {
            val response = client.put("$normalizedBaseUrl/api/profile/me") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    UpdateProfileRequest(
                        fullName = fullName,
                        email = email
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    UpdateProfileResult.Success(response.body<ProfileResponse>())
                }

                HttpStatusCode.BadRequest,
                HttpStatusCode.Conflict -> {
                    UpdateProfileResult.Failure(response.errorMessage("Could not update profile."))
                }

                HttpStatusCode.Unauthorized -> {
                    UpdateProfileResult.Failure("Your session expired. Please sign in again.")
                }

                else -> {
                    UpdateProfileResult.Failure("Could not update profile. Status: ${response.status.value}")
                }
            }
        } catch (_: Exception) {
            UpdateProfileResult.Failure("Cannot connect to the server.")
        }
    }

    suspend fun changePassword(
        token: String,
        currentPassword: String,
        newPassword: String
    ): ChangePasswordResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/profile/password") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    ChangePasswordRequest(
                        currentPassword = currentPassword,
                        newPassword = newPassword
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    ChangePasswordResult.Success
                }

                HttpStatusCode.BadRequest,
                HttpStatusCode.Forbidden,
                HttpStatusCode.TooManyRequests -> {
                    ChangePasswordResult.Failure(response.errorMessage("Could not update password."))
                }

                HttpStatusCode.Unauthorized -> {
                    ChangePasswordResult.Failure("Your session expired. Please sign in again.")
                }

                else -> {
                    ChangePasswordResult.Failure("Could not update password. Status: ${response.status.value}")
                }
            }
        } catch (_: Exception) {
            ChangePasswordResult.Failure("Cannot connect to the server.")
        }
    }

    private suspend fun io.ktor.client.statement.HttpResponse.errorMessage(
        fallback: String
    ): String {
        return try {
            body<ErrorResponse>().message
        } catch (_: Exception) {
            fallback
        }
    }
}