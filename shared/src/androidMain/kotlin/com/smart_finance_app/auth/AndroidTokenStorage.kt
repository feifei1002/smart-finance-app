package com.smart_finance_app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

class AndroidTokenStorage(context: Context) : TokenStorage {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "smart_finance_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getRefreshToken(): String? {
        return preferences.getString("refresh_token", null)
    }

    override fun saveRefreshToken(refreshToken: String) {
        preferences.edit { putString("refresh_token", refreshToken) }
    }

    override fun clearRefreshToken() {
        preferences.edit { remove("refresh_token") }
    }
}