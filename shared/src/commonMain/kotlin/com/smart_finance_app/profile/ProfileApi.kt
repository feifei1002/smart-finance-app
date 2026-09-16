package com.smart_finance_app.profile

import com.smart_finance_app.StringKey
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
    val email: String,
    val currentPassword: String? = null
)

@Serializable
private data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)


sealed interface ProfileResult {
    data class Success(val profile: ProfileResponse) : ProfileResult
    data class Failure(val message: StringKey) : ProfileResult
}

sealed interface UpdateProfileResult {
    data class Success(val profile: ProfileResponse) : UpdateProfileResult
    data class Failure(val message: StringKey) : UpdateProfileResult
}

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data class Failure(val message: StringKey) : ChangePasswordResult
}

class ProfileApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun updateProfile(
        token: String,
        fullName: String,
        email: String,
        currentPassword: String? = null
    ): UpdateProfileResult {
        return try {
            val response = client.put("$normalizedBaseUrl/api/profile/me") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    UpdateProfileRequest(
                        fullName = fullName,
                        email = email,
                        currentPassword = currentPassword
                    )
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    UpdateProfileResult.Success(response.body<ProfileResponse>())
                }

                HttpStatusCode.BadRequest -> {
                    UpdateProfileResult.Failure(StringKey.EDIT_PROFILE_ERROR_UPDATE_FAILED)
                }

                HttpStatusCode.Conflict -> {
                    UpdateProfileResult.Failure(StringKey.EDIT_PROFILE_ERROR_EMAIL_EXISTS)
                }

                HttpStatusCode.Forbidden -> {
                    UpdateProfileResult.Failure(StringKey.EDIT_PROFILE_ERROR_WRONG_PASSWORD)
                }

                HttpStatusCode.TooManyRequests -> {
                    UpdateProfileResult.Failure(StringKey.EDIT_PROFILE_ERROR_TOO_MANY_ATTEMPTS)
                }

                HttpStatusCode.Unauthorized -> {
                    UpdateProfileResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                else -> {
                    UpdateProfileResult.Failure(StringKey.EDIT_PROFILE_ERROR_UPDATE_FAILED)
                }
            }
        } catch (_: Exception) {
            UpdateProfileResult.Failure(StringKey.COMMON_ERROR_SERVER)
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

                HttpStatusCode.BadRequest -> {
                    ChangePasswordResult.Failure(StringKey.EDIT_PROFILE_ERROR_PASSWORD_UPDATE_FAILED)
                }

                HttpStatusCode.Forbidden -> {
                    ChangePasswordResult.Failure(StringKey.EDIT_PROFILE_ERROR_WRONG_PASSWORD)
                }

                HttpStatusCode.TooManyRequests -> {
                    ChangePasswordResult.Failure(StringKey.EDIT_PROFILE_ERROR_PASSWORD_TOO_MANY_ATTEMPTS)
                }

                HttpStatusCode.Unauthorized -> {
                    ChangePasswordResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                else -> {
                    ChangePasswordResult.Failure(StringKey.EDIT_PROFILE_ERROR_PASSWORD_UPDATE_FAILED)
                }
            }
        } catch (_: Exception) {
            ChangePasswordResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}