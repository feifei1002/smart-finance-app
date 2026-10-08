package com.smart_finance_app.server.banking.accounts

import com.smart_finance_app.server.Database
import com.smart_finance_app.server.Encryption
import com.smart_finance_app.server.banking.models.AccountResponse
import com.smart_finance_app.server.banking.models.StoredAccount
import com.smart_finance_app.server.banking.truelayer.TrueLayerAccount
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.text.takeLast
import kotlin.use

internal fun getConnectedAccountsForUser(userId: UUID): List<AccountResponse> =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT account_id, bank_name, provider, account_number
            FROM connected_accounts
            WHERE user_id = ? AND connection_status = 'connected'
            ORDER BY created_at DESC
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                val accounts = mutableListOf<AccountResponse>()
                while (result.next()) {
                    val realAccountNumber = result.getString("account_number")
                    // Mask the real bank account number — show only last 4 digits
                    val masked = if (!realAccountNumber.isNullOrBlank())
                        realAccountNumber.takeLast(4)
                    else
                        "****"  // fallback if bank didn't return account number
                    accounts.add(
                        AccountResponse(
                            accountId = result.getString("account_id"),
                            bankName = result.getString("bank_name"),
                            maskedNumber = masked,
                            provider = result.getString("provider")
                        )
                    )
                }
                accounts
            }
        }
    }

/**
 * Returns connected accounts with tokens — used internally for API calls.
 */
internal fun getStoredAccountsWithTokens(userId: UUID): List<StoredAccount> =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, account_id, bank_name, access_token, refresh_token, token_expiry
            FROM connected_accounts
            WHERE user_id = ? AND connection_status = 'connected'
            ORDER BY created_at DESC
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                val accounts = mutableListOf<StoredAccount>()
                while (result.next()) {
                    accounts.add(
                        StoredAccount(
                            dbId = result.getObject("id", UUID::class.java),
                            accountName = result.getString("bank_name"),
                            accountId = result.getString("account_id"),
                            accessToken = Encryption.decrypt(result.getString("access_token")),
                            refreshToken = Encryption.decrypt(result.getString("refresh_token")),
                            tokenExpiry = result.getTimestamp("token_expiry").toInstant()
                        )
                    )
                }
                accounts
            }
        }
    }

internal fun disconnectConnectedAccount(userId: UUID, accountId: String): Boolean =
    Database.dataSource.connection.use { connection ->
        try {
            val updatedRows = connection.prepareStatement(
                """
                UPDATE connected_accounts
                SET connection_status = 'disconnected',
                    access_token = NULL,
                    refresh_token = NULL,
                    token_expiry = NULL,
                    updated_at = NOW()
                WHERE user_id = ?
                  AND account_id = ?
                  AND connection_status = 'connected'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, accountId)
                statement.executeUpdate()
            }

            connection.commit()
            updatedRows > 0
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

/**
 * Saves each connected account to the connected_accounts table.
 * Uses INSERT ... ON CONFLICT DO UPDATE so reconnecting the same
 * bank just refreshes the tokens rather than creating duplicates.
 */
internal fun saveConnectedAccounts(
    connection: Connection,
    userId: UUID,
    accounts: List<TrueLayerAccount>,
    accessToken: String,
    refreshToken: String,
    tokenExpiry: Instant,
    providerId: String,
    providerName: String
) {
    accounts.forEach { account ->
        connection.prepareStatement(
            """
            INSERT INTO connected_accounts
                (user_id, bank_name, account_id, access_token, refresh_token, token_expiry, provider, account_number, connection_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'connected')
            ON CONFLICT (user_id, account_id)
            DO UPDATE SET
                access_token = EXCLUDED.access_token,
                refresh_token = EXCLUDED.refresh_token,
                token_expiry = EXCLUDED.token_expiry,
                account_number = EXCLUDED.account_number,
                connection_status = 'connected',
                updated_at = NOW()
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setString(2, providerName)
            statement.setString(3, account.accountId)
            statement.setString(4, Encryption.encrypt(accessToken))
            statement.setString(5, Encryption.encrypt(refreshToken))
            statement.setTimestamp(6, Timestamp.from(tokenExpiry))
            statement.setString(7, providerId)
            statement.setString(8, account.accountNumber?.number)
            statement.executeUpdate()
        }
    }
}
