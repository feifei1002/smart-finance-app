package com.smart_finance_app.server.banking.accounts

import com.google.gson.reflect.TypeToken
import com.smart_finance_app.server.Database
import com.smart_finance_app.server.Encryption
import com.smart_finance_app.server.banking.config.bankingGson
import com.smart_finance_app.server.banking.config.maxAccountsForSubscription
import com.smart_finance_app.server.banking.truelayer.TrueLayerAccount
import com.smart_finance_app.server.subscriptions.getSubscriptionStatus
import java.util.UUID
import kotlin.use

internal fun saveSelectedBankAccounts(
    userId: UUID,
    state: String,
    selectedAccountIds: List<String>
): Boolean {
    if (selectedAccountIds.isEmpty()) return false

    return Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtext(?))"
            ).use { statement ->
                statement.setString(1, userId.toString())
                statement.executeQuery().close()
            }

            val session = connection.prepareStatement(
                """
                SELECT provider_id, provider_name, access_token, refresh_token, token_expiry, available_accounts
                FROM bank_connection_sessions
                WHERE user_id = ?
                  AND state = ?
                  AND status = 'awaiting_account_selection'
                  AND expires_at > NOW()
                FOR UPDATE
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, state)

                statement.executeQuery().use { result ->
                    if (!result.next()) return@use null

                    PendingBankSelection(
                        providerId = result.getString("provider_id"),
                        providerName = result.getString("provider_name"),
                        accessToken = Encryption.decrypt(result.getString("access_token")),
                        refreshToken = Encryption.decrypt(result.getString("refresh_token")),
                        tokenExpiry = result.getTimestamp("token_expiry").toInstant(),
                        accountsJson = result.getString("available_accounts")
                    )
                }
            } ?: return@use false

            val accountListType = object : TypeToken<List<TrueLayerAccount>>() {}.type
            val accounts: List<TrueLayerAccount> = bankingGson.fromJson(session.accountsJson, accountListType)

            val selectedAccounts = accounts.filter { it.accountId in selectedAccountIds }

            val connectedAccountIds = getConnectedAccountsForUser(userId)
                .map { it.accountId }
                .toSet()

            if (selectedAccounts.any { it.accountId in connectedAccountIds }) {
                connection.rollback()
                return@use false
            }

            if (selectedAccounts.size != selectedAccountIds.toSet().size) {
                connection.rollback()
                return@use false
            }

            val connectedCount = getConnectedAccountsForUser(userId).size
            val subscriptionStatus = getSubscriptionStatus(userId)
            val maxAccounts = maxAccountsForSubscription(subscriptionStatus)

            if (connectedCount + selectedAccounts.size > maxAccounts) {
                connection.rollback()
                return@use false
            }

            saveConnectedAccounts(
                connection = connection,
                userId = userId,
                accounts = selectedAccounts,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                tokenExpiry = session.tokenExpiry,
                providerId = session.providerId,
                providerName = session.providerName
            )

            connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET status = 'completed',
                    access_token = NULL,
                    refresh_token = NULL,
                    token_expiry = NULL,
                    available_accounts = NULL,
                    updated_at = NOW()
                WHERE user_id = ?
                  AND state = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, state)
                statement.executeUpdate()
            }

            connection.commit()
            true
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}