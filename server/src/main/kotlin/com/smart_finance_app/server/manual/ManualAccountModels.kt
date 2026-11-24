package com.smart_finance_app.server.manual

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
internal enum class ManualAccountType(val databaseValue: String) {
    @SerialName("bank_account") BANK_ACCOUNT("bank_account"),
    @SerialName("cash") CASH("cash"),
    @SerialName("savings") SAVINGS("savings"),
    @SerialName("credit_card") CREDIT_CARD("credit_card")
}

@Serializable
internal data class CreateManualAccountRequest(
    val name: String,
    val type: ManualAccountType,
    val currency: String,
    val startingBalance: String
)

@Serializable
internal data class ManualAccountResponse(
    val id: String,
    val name: String,
    val type: ManualAccountType,
    val currency: String,
    val startingBalance: String,
    val currentBalance: String
)

@Serializable
internal data class ManualAccountsPageResponse(
    val accounts: List<ManualAccountResponse>,
    val nextCursor: String? = null
)

internal data class ManualAccountCreateResult(
    val account: ManualAccountResponse,
    val created: Boolean
)

internal class ManualAccountCreateConflict : IllegalStateException()

internal data class ValidatedManualAccount(
    val name: String,
    val type: ManualAccountType,
    val currency: String,
    val startingBalance: BigDecimal
)