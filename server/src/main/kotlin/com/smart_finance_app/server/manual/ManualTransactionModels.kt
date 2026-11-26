package com.smart_finance_app.server.manual

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Serializable
internal enum class ManualTransactionType {
    @SerialName("income") INCOME,
    @SerialName("expense") EXPENSE
}

@Serializable
internal data class CreateManualTransactionRequest(
    val accountId: String,
    val type: ManualTransactionType,
    val amount: String,
    val description: String,
    val category: String,
    val date: String
)

@Serializable
internal data class ManualTransactionResponse(
    val id: String,
    val date: String,
    val merchantName: String,
    val category: String,
    val accountName: String,
    val accountId: String,
    val amount: Double,
    val currency: String,
    val merchantLogoUrl: String? = null,
    val transactionStatus: String = "settled"
)

@Serializable
internal data class ManualTransactionsPageResponse(
    val transactions: List<ManualTransactionResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasMore: Boolean
)

internal data class ManualTransactionCreateResult(
    val transaction: ManualTransactionResponse,
    val created: Boolean
)

internal class ManualTransactionCreateConflict : IllegalStateException()
internal class ManualTransactionAccountNotFound : IllegalStateException()

internal data class ValidatedManualTransaction(
    val accountId: UUID,
    val type: ManualTransactionType,
    val signedAmount: BigDecimal,
    val description: String,
    val category: String,
    val date: LocalDate
)

internal object ManualTransactionCategories {
    const val INCOME = "Income"
    const val TRANSFERS = "Transfers"
    const val OTHERS = "Others"

    val expense = listOf(
        "Food & Dining",
        "Shopping & Personal",
        "Bills & Housing",
        "Entertainment & Subscriptions",
        "Transportation",
        TRANSFERS,
        OTHERS
    )

    val income = listOf(INCOME, TRANSFERS, OTHERS)

    fun forType(type: ManualTransactionType): List<String> = when (type) {
        ManualTransactionType.INCOME -> income
        ManualTransactionType.EXPENSE -> expense
    }
}