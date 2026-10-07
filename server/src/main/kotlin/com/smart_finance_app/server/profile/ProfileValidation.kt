package com.smart_finance_app.server.profile

internal fun isValidEmail(email: String): Boolean {
    return email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
}

internal fun validateProfileUpdate(fullName: String, email: String): String? {
    return when {
        fullName.isBlank() -> "Full name is required"
        fullName.length > 100 -> "Full name must be 100 characters or less"
        email.length > 254 -> "Email address must be 254 characters or less"
        !isValidEmail(email) -> "Please enter a valid email address"
        else -> null
    }
}

internal fun validateNewPassword(password: String): String? {
    val passwordBytes = password.encodeToByteArray()

    return when {
        password.length < 8 || passwordBytes.size > 72 -> "Password must be at least 8 characters"
        else -> null
    }
}