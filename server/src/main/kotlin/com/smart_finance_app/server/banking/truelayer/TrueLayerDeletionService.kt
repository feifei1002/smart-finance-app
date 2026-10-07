package com.smart_finance_app.server.banking.truelayer

import com.smart_finance_app.server.banking.config.bankingHttpClient
import com.smart_finance_app.server.banking.config.bankingLogger
import com.smart_finance_app.server.banking.accounts.getStoredAccountsWithTokens
import okhttp3.Request
import java.util.UUID

internal fun deleteTrueLayerDataForUser(userId: UUID) {
    getStoredAccountsWithTokens(userId)
        .distinctBy { it.refreshToken } // accounts from one bank login share a token
        .forEach { stored ->
            runCatching {
                val token = ensureFreshToken(stored)
                val request = Request.Builder()
                    .url("${TrueLayerConfig.AUTH_BASE_URL}/api/delete")
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
                bankingHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        bankingLogger.warn(
                            "TrueLayer delete failed for user {} / account {}: HTTP {}",
                            userId, stored.accountId, response.code
                        )
                    }
                }
            }.onFailure { exception ->
                // Token refresh or network error — never log the token itself
                bankingLogger.warn(
                    "TrueLayer delete errored for user {} / account {}",
                    userId, stored.accountId, exception
                )
            }
        }
}