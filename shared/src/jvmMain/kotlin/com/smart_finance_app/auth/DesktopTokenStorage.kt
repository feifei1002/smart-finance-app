package com.smart_finance_app.auth

import java.util.prefs.Preferences

class DesktopTokenStorage : TokenStorage {
    private val preferences = Preferences.userRoot().node("smart_finance_auth")

    override fun getRefreshToken(): String? {
        return preferences.get("refresh_token", null)
    }

    override fun saveRefreshToken(refreshToken: String) {
        preferences.put("refresh_token", refreshToken)
    }

    override fun clearRefreshToken() {
        preferences.remove("refresh_token")
    }
}