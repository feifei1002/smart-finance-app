package com.smart_finance_app.server.banking.models

import kotlinx.serialization.Serializable
import java.util.UUID
import java.time.Instant

data class StoredAccount(
    val accountId: String,
    val accountName: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiry: Instant,
    val dbId: UUID
)

@Serializable
data class CreateBankConnectionRequest(val providerId: String, val providerName: String)

// ── Response models ───────────────────────────────────────────────────────────

@Serializable
data class ConnectBankResponse(val authUrl: String, val state: String)

@Serializable
data class AccountResponse(
    val accountId: String,
    val bankName: String,
    val maskedNumber: String,
    val provider: String
)

@Serializable
data class SelectableBankAccountResponse(
    val accountId: String,
    val bankName: String,
    val maskedNumber: String
)

@Serializable
data class SelectBankAccountsRequest(
    val accountIds: List<String>
)

@Serializable
data class BalanceResponse(
    val accountId: String,
    val current: Double,
    val available: Double,
    val currency: String
)

@Serializable
data class TransactionResponse(
    val transactionId: String,
    val timestamp: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val type: String,       // CREDIT or DEBIT
    val merchantName: String?,
    val accountId: String? = null   // which connected account this transaction belongs to
)

@Serializable
data class ImportedTransactionResponse(
    val id: String,
    val date: String,
    val merchantName: String,
    val category: String,
    val accountName: String,
    val accountId: String? = null,
    val amount: Double,
    val currency: String,
    val merchantLogoUrl: String? = null
)

@Serializable
data class TransactionSyncResponse(
    val importedCount: Int,
    val duplicateCount: Int,
    val lastSuccessfulSyncAt: String?
)

@Serializable
data class PaginatedTransactionsResponse(
    val transactions: List<ImportedTransactionResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasMore: Boolean
)

@Serializable
data class UpdateTransactionCategoryRequest(
    val category: String
)
@Serializable
data class UpdateTransactionCategoryResponse(
    val id: String,
    val category: String
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

@Serializable
data class BankConnectionStatusResponse(val status: String)