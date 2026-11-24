package com.smart_finance_app.accounts.manual

import com.smart_finance_app.StringKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.coroutines.cancellation.CancellationException

sealed interface ManualAccountsResult {
    data class Success(
        val accounts: List<ManualAccountResponse>,
        val nextCursor: String? = null
    ) : ManualAccountsResult
    data class Failure(val message: StringKey) : ManualAccountsResult
}

sealed interface CreateManualAccountResult {
    data class Success(val account: ManualAccountResponse) : CreateManualAccountResult
    data class Failure(val message: StringKey) : CreateManualAccountResult
    data object Conflict: CreateManualAccountResult
}

class ManualAccountsApi(baseUrl: String, private val client: HttpClient) {
    private val url = baseUrl.trimEnd('/') + "/api/accounts"

    suspend fun getAccounts(token: String, cursor: String? = null): ManualAccountsResult {
        if (token.isBlank()) {
            return ManualAccountsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
        }
        return try {
            val response = client.get(url) {
                bearerAuth(token)
                parameter("limit", 50)
                cursor?.let { parameter("cursor", it) }
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val page = response.body<ManualAccountsPageResponse>()
                    ManualAccountsResult.Success(
                        accounts = page.accounts,
                        nextCursor = page.nextCursor
                    )
                }
                HttpStatusCode.Unauthorized ->
                    ManualAccountsResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
                else -> ManualAccountsResult.Failure(StringKey.MANUAL_ACCOUNTS_LOAD_ERROR)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ResponseException) {
            ManualAccountsResult.Failure(
                if (exception.response.status == HttpStatusCode.Unauthorized) {
                    StringKey.COMMON_SESSION_EXPIRED
                } else {
                    StringKey.MANUAL_ACCOUNTS_LOAD_ERROR
                }
            )
        } catch (_: Exception) {
            ManualAccountsResult.Failure(StringKey.COMMON_ERROR_SERVER)
        }
    }

    suspend fun createAccount(
        token: String,
        request: CreateManualAccountRequest,
        requestId: String
    ): CreateManualAccountResult {
        if (token.isBlank()) {
            return CreateManualAccountResult.Failure(StringKey.COMMON_SESSION_EXPIRED)
        }
        return try {
            val response = client.post(url) {
                bearerAuth(token)
                header("Idempotency-Key", requestId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK ->
                    CreateManualAccountResult.Success(response.body())
                HttpStatusCode.Conflict -> CreateManualAccountResult.Conflict
                else -> CreateManualAccountResult.Failure(createFailureKey(response.status))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ResponseException) {
            if (exception.response.status == HttpStatusCode.Conflict) {
                CreateManualAccountResult.Conflict
            } else {
                CreateManualAccountResult.Failure(
                    createFailureKey(exception.response.status)
                )
            }
        } catch (_: Exception) {
            CreateManualAccountResult.Failure(StringKey.MANUAL_ACCOUNTS_CREATE_ERROR)
        }
    }

    private fun createFailureKey(status: HttpStatusCode): StringKey = when (status) {
        HttpStatusCode.Unauthorized -> StringKey.COMMON_SESSION_EXPIRED
        HttpStatusCode.BadRequest, HttpStatusCode.UnprocessableEntity ->
            StringKey.MANUAL_ACCOUNTS_VALIDATION_ERROR
        else -> StringKey.MANUAL_ACCOUNTS_CREATE_ERROR
    }
}