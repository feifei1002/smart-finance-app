package com.smart_finance_app.auth

interface TokenStorage {
    fun getRefreshToken(): String?
    fun saveRefreshToken(refreshToken: String)
    fun clearRefreshToken()
}

