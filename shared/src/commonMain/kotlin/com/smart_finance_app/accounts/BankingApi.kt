package com.smart_finance_app.accounts

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class CreateBankConnectionRequest(val providerId: String, val providerName: String)

@Serializable
private data class SelectBankAccountsRequest(
    val accountIds: List<String>
)

@Serializable
private data class ConnectBankResponse(
    val authUrl: String,
    val state: String
)

@Serializable
private data class BankConnectionStatusResponse(val status: String)

@Serializable
data class SelectableBankAccountResponse(
    val accountId: String,
    val bankName: String,
    val maskedNumber: String
)

@Serializable
data class ConnectedAccountResponse(
    val accountId: String,
    val bankName: String,
    val maskedNumber: String,
    val provider: String
)

@Serializable
data class BankProviderVariantResponse(
    val id: String,
    val label: String,
    val name: String
)
@Serializable
data class BankProviderResponse(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val variants: List<BankProviderVariantResponse> = emptyList()
)
sealed interface BankConnectionResult {
    data class Success(val authUrl: String, val state: String): BankConnectionResult
    data object AccountLimitReached : BankConnectionResult
    data class Failure(val message: StringKey): BankConnectionResult
}

sealed interface SelectableAccountsResult {
    data class Success(val accounts: List<SelectableBankAccountResponse>) : SelectableAccountsResult
    data class Failure(val message: StringKey) : SelectableAccountsResult
}

sealed interface SaveSelectedAccountsResult {
    data object Success : SaveSelectedAccountsResult
    data class Failure(val message: StringKey) : SaveSelectedAccountsResult
}

sealed interface DisconnectAccountResult {
    data object Success : DisconnectAccountResult
    data class Failure(val message: StringKey) : DisconnectAccountResult
}

sealed interface ConnectedAccountResult {
    data class Success(val accounts: List<ConnectedAccountResponse>): ConnectedAccountResult
    data class Failure(val message: StringKey): ConnectedAccountResult
}

sealed interface BankProviderResult {
    data class Success(val providers: List<BankProviderResponse>): BankProviderResult
    data class Failure(val message: StringKey): BankProviderResult
}

sealed interface BankConnectionStatusResult {
    data class Success(val status: String) : BankConnectionStatusResult
    data class Failure(val message: StringKey) : BankConnectionStatusResult
}

/**
 * Client API for banking-related backend endpoints.
 *
 * This class talks to the app backend, not directly to TrueLayer.
 * The backend is responsible for creating TrueLayer sessions and storing accounts.
 */
class BankingApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    /**
     * Starts a bank connection session for the selected bank.
     *
     * The backend returns a TrueLayer authUrl, which the frontend opens in the browser.
     */
    suspend fun createConnectionSession(
        token: String,
        bank: BankOption
    ): BankConnectionResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/banking/connect") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    CreateBankConnectionRequest(
                        providerId = bank.id,
                        providerName = bank.name
                    )
                )
            }

            when(response.status) {
                HttpStatusCode.OK -> {
                    val body = response.body<ConnectBankResponse>()
                    BankConnectionResult.Success(body.authUrl, body.state)
                }

                HttpStatusCode.Forbidden -> BankConnectionResult.AccountLimitReached
                HttpStatusCode.Unauthorized -> {
                    BankConnectionResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                else -> {
                    BankConnectionResult.Failure(StringKey.BANKING_ERROR_CONNECT_FAILED)
                }
            }
        } catch (_: Exception) {
            BankConnectionResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    /**
     * Loads the user's connected bank accounts from the backend.
     *
     * Used to display saved accounts in the Accounts tab.
     */

    suspend fun getConnectedAccounts(token: String): ConnectedAccountResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/banking/accounts") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    ConnectedAccountResult.Success(response.body())
                }

                HttpStatusCode.Unauthorized -> {
                    ConnectedAccountResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                else -> {
                    ConnectedAccountResult.Failure(StringKey.BANKING_ERROR_LOAD_ACCOUNTS_FAILED)
                }
            }
        } catch (_: Exception) {
            ConnectedAccountResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun getSelectableAccounts(
        token: String,
        state: String
    ): SelectableAccountsResult {
        return try {
            val response = client.get(
                "$normalizedBaseUrl/api/banking/connection-session/$state/accounts"
            ) {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    SelectableAccountsResult.Success(response.body())
                }

                HttpStatusCode.Unauthorized -> {
                    SelectableAccountsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                HttpStatusCode.NotFound -> {
                    SelectableAccountsResult.Failure(StringKey.BANKING_ERROR_CONNECT_FAILED)
                }

                else -> {
                    SelectableAccountsResult.Failure(StringKey.BANKING_ERROR_LOAD_ACCOUNTS_FAILED)
                }
            }
        } catch (_: Exception) {
            SelectableAccountsResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun saveSelectedAccounts(
        token: String,
        state: String,
        accountIds: List<String>
    ): SaveSelectedAccountsResult {
        return try {
            val response = client.post(
                "$normalizedBaseUrl/api/banking/connection-session/$state/accounts"
            ) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(SelectBankAccountsRequest(accountIds))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    SaveSelectedAccountsResult.Success
                }

                HttpStatusCode.Unauthorized -> {
                    SaveSelectedAccountsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                HttpStatusCode.BadRequest,
                HttpStatusCode.Forbidden -> {
                    SaveSelectedAccountsResult.Failure(StringKey.BANKING_ERROR_CONNECT_FAILED)
                }

                else -> {
                    SaveSelectedAccountsResult.Failure(StringKey.COMMON_ERROR_SERVER)
                }
            }
        } catch (_: Exception) {
            SaveSelectedAccountsResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    /**
     * Loads available bank providers from the backend.
     *
     * The backend fetches these from TrueLayer and returns only the fields
     * needed by the UI.
     */
    suspend fun getBankProviders(token: String): BankProviderResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/banking/providers") {
                bearerAuth(token)
            }

            when(response.status) {
                HttpStatusCode.OK -> {
                    BankProviderResult.Success(response.body())
                }

                HttpStatusCode.Unauthorized -> {
                    BankProviderResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                else -> {
                    BankProviderResult.Failure(StringKey.BANKING_ERROR_LOAD_PROVIDERS_FAILED)
                }
            }
        } catch (_: Exception) {
            BankProviderResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun getConnectionStatus(token: String, state: String): BankConnectionStatusResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/banking/connection-session/$state") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    BankConnectionStatusResult.Success(
                        response.body<BankConnectionStatusResponse>().status
                    )
                }

                HttpStatusCode.Unauthorized -> {
                    BankConnectionStatusResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }

                else -> {
                    BankConnectionStatusResult.Failure(StringKey.BANKING_ERROR_CONNECT_FAILED)
                }
            }
        } catch (_: Exception) {
            BankConnectionStatusResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun disconnectAccount(
        token: String,
        accountId: String
    ): DisconnectAccountResult {
        return try {
            val response = client.delete("$normalizedBaseUrl/api/banking/accounts/$accountId") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> DisconnectAccountResult.Success
                HttpStatusCode.Unauthorized -> {
                    DisconnectAccountResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                }
                else -> {
                    DisconnectAccountResult.Failure(StringKey.BANKING_ERROR_DISCONNECT_ACCOUNT_FAILED)
                }
            }
        } catch (_: Exception) {
            DisconnectAccountResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }
}