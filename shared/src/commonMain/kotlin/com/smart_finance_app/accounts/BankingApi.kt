package com.smart_finance_app.accounts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CreateBankConnectionRequest(val providerId: String, val providerName: String)

@Serializable
private data class ConnectBankResponse(val authUrl: String)

@Serializable
data class ConnectedAccountResponse(val accountId: String, val bankName: String, val maskedNumber: String, val provider: String)

@Serializable
data class BankProviderResponse(val id: String, val name: String, val logoUrl: String? = null)
sealed interface BankConnectionResult {
    data class Success(val authUrl: String): BankConnectionResult
    data class Failure(val message: String): BankConnectionResult
}

sealed interface ConnectedAccountResult {
    data class Success(val accounts: List<ConnectedAccountResponse>): ConnectedAccountResult
    data class Failure(val message: String): ConnectedAccountResult
}

sealed interface BankProviderResult {
    data class Success(val providers: List<BankProviderResponse>): BankProviderResult
    data class Failure(val message: String): BankProviderResult
}

/**
 * Client API for banking-related backend endpoints.
 *
 * This class talks to the app backend, not directly to TrueLayer.
 * The backend is responsible for creating TrueLayer sessions and storing accounts.
 */
class BankingApi(baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys= true })
        }
    }

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
                    BankConnectionResult.Success(body.authUrl)
                }

                HttpStatusCode.Unauthorized -> {
                    BankConnectionResult.Failure("Your session expired. Please connect again.")
                }

                else -> {
                    BankConnectionResult.Failure(
                        "Could not start bank connection. Status: ${response.status.value}"
                    )
                }
            }
        } catch (_: Exception) {
            BankConnectionResult.Failure("Cannot connect to the server.")
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
                    ConnectedAccountResult.Failure("Your session expired. Please sign in again")
                }

                else -> {
                    ConnectedAccountResult.Failure("Could not load connected accounts. Status: ${response.status.value}")
                }
            }
        } catch (_: Exception) {
            ConnectedAccountResult.Failure("Cannot connect to the server.")
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
                    BankProviderResult.Failure("Your session expired. Please sign in again.")
                }

                else -> {
                    BankProviderResult.Failure("Could not load bank providers. Status: ${response.status.value}")
                }
            }
        } catch (_: Exception) {
            BankProviderResult.Failure("Cannot connect to the server.")
        }
    }

    fun close() {
        client.close()
    }
}