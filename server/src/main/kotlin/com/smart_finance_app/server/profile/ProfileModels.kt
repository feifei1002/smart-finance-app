package com.smart_finance_app.server.profile

import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val fullName: String,
    val email: String
)

@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val email: String,
    val currentPassword: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ChangePasswordResponse(
    val message: String
)

internal data class ProfileWithPasswordHash(
    val fullName: String,
    val email: String,
    val passwordHash: String
)