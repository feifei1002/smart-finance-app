package com.smart_finance_app.server.banking.accounts

import com.google.gson.reflect.TypeToken
import com.smart_finance_app.server.Database
import com.smart_finance_app.server.Encryption
import com.smart_finance_app.server.banking.config.bankingGson
import com.smart_finance_app.server.banking.models.SelectableBankAccountResponse
import com.smart_finance_app.server.banking.truelayer.TrueLayerAccount
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.use

internal data class PendingBankSelection(
    val providerId: String,
    val providerName: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiry: Instant,
    val accountsJson: String
)

internal data class BankConnectionSession(
    val userId: UUID,
    val providerId: String,
    val providerName: String
)

/**
 * Creates a pending bank connection session before sending the user to TrueLayer.
 *
 * The random state value is stored so the callback can be validated later.
 * This prevents trusting user IDs directly from the callback URL.
 */
internal fun createBankConnectionSession(userId: UUID, state: String, providerId: String, providerName: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO bank_connection_sessions
                    (user_id, state, provider_id, provider_name, status)
                    VALUES (?, ?, ?, ?, 'pending')
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, state)
                statement.setObject(3, providerId)
                statement.setObject(4, providerName)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

internal fun getBankConnectionSessionStatus(userId: UUID, state: String): String? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT status FROM bank_connection_sessions
                WHERE user_id = ? AND state = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setString(2, state)

            statement.executeQuery().use { result ->
                if (result.next()) result.getString("status") else null
            }
        }
    }

internal fun failBankConnectionSession(state: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET status = 'failed',
                    access_token = NULL,
                    refresh_token = NULL,
                    token_expiry = NULL,
                    available_accounts = NULL,
                    completed_at = NOW(),
                    updated_at = NOW()
                WHERE state = ?
                  AND status IN ('pending', 'processing')
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, state)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

/**
 * Finds a pending bank connection session by its state value.
 *
 * Returns null if the state is unknown, already completed, failed, or expired.
 */
internal fun getPendingBankConnectionSession(state: String): BankConnectionSession? =
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET status = 'processing'
                WHERE state = ?
                  AND status = 'pending'
                  AND created_at >= now() - interval '15 minutes'
                RETURNING user_id, provider_id, provider_name
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, state)
                statement.executeQuery().use { result ->
                    val session = if (!result.next()) null else BankConnectionSession(
                        userId = result.getObject("user_id", UUID::class.java),
                        providerId = result.getString("provider_id"),
                        providerName = result.getString("provider_name")
                    )
                    connection.commit()
                    session
                }
            }
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

internal fun expireOldBankConnectionSelections() {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET access_token = NULL,
                    refresh_token = NULL,
                    token_expiry = NULL,
                    available_accounts = NULL,
                    status = 'expired',
                    updated_at = NOW()
                WHERE (
                    status = 'awaiting_account_selection' AND expires_at <= NOW()
                )
                OR (
                    status IN ('pending', 'processing')
                    AND created_at <= NOW() - INTERVAL '15 minutes'
                )
                """.trimIndent()
            ).use { statement ->
                statement.executeUpdate()
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

/**
 * Returns connected accounts for the accounts screen (no tokens exposed).
 */
internal fun savePendingBankAccountSelection(
    state: String,
    accessToken: String,
    refreshToken: String,
    tokenExpiry: Instant,
    accounts: List<TrueLayerAccount>
) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET status = 'awaiting_account_selection',
                    access_token = ?,
                    refresh_token = ?,
                    token_expiry = ?,
                    available_accounts = ?::jsonb,
                    expires_at = NOW() + INTERVAL '15 minutes',
                    updated_at = NOW()
                WHERE state = ?
                  AND status = 'processing'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, Encryption.encrypt(accessToken))
                statement.setString(2, Encryption.encrypt(refreshToken))
                statement.setTimestamp(3, Timestamp.from(tokenExpiry))
                statement.setString(4, bankingGson.toJson(accounts))
                statement.setString(5, state)
                val updatedRows = statement.executeUpdate()

                if (updatedRows != 1) {
                    error("Could not update pending bank connection session")
                }
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

internal fun cancelPendingBankAccountSelection(
    userId: UUID,
    state: String
): Boolean {
    return Database.dataSource.connection.use { connection ->
        try {
            val updatedRows = connection.prepareStatement(
                """
                UPDATE bank_connection_sessions
                SET status = 'cancelled',
                    access_token = NULL,
                    refresh_token = NULL,
                    token_expiry = NULL,
                    available_accounts = NULL,
                    updated_at = NOW()
                WHERE user_id = ?
                  AND state = ?
                  AND status = 'awaiting_account_selection'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, state)
                statement.executeUpdate()
            }

            connection.commit()
            updatedRows > 0
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

internal fun getPendingSelectableAccounts(
    userId: UUID,
    state: String
): List<SelectableBankAccountResponse>? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT provider_name, available_accounts
            FROM bank_connection_sessions
            WHERE user_id = ?
              AND state = ?
              AND status = 'awaiting_account_selection'
              AND expires_at > NOW()
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setString(2, state)

            statement.executeQuery().use { result ->
                if (!result.next()) return@use null

                val providerName = result.getString("provider_name")
                val json = result.getString("available_accounts")

                val accountListType = object : TypeToken<List<TrueLayerAccount>>() {}.type
                val accounts: List<TrueLayerAccount> = bankingGson.fromJson(json, accountListType)

                val connectedAccountIds = getConnectedAccountsForUser(userId)
                    .map { it.accountId }
                    .toSet()

                accounts
                    .filter { account -> account.accountId !in connectedAccountIds }
                    .map { account ->
                        SelectableBankAccountResponse(
                            accountId = account.accountId,
                            bankName = providerName,
                            maskedNumber = account.accountNumber?.number?.takeLast(4) ?: "****"
                        )
                    }
            }
        }
    }