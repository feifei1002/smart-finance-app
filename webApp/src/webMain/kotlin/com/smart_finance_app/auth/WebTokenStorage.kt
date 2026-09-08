package com.smart_finance_app.auth

class WebTokenStorage : TokenStorage {
    override fun getRefreshToken(): String? = null
    override fun saveRefreshToken(refreshToken: String) = Unit
    override fun clearRefreshToken() = Unit
}