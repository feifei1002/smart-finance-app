package com.smart_finance_app.accounts.manual

import com.smart_finance_app.StringKey
import com.smart_finance_app.currency.CurrencyController
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ManualAccountType {
    @SerialName("bank_account") BANK_ACCOUNT,
    @SerialName("cash") CASH,
    @SerialName("savings") SAVINGS,
    @SerialName("credit_card") CREDIT_CARD;

    val labelKey: StringKey
        get() = when (this) {
            BANK_ACCOUNT -> StringKey.MANUAL_ACCOUNTS_BANK_ACCOUNT
            CASH -> StringKey.MANUAL_ACCOUNTS_CASH
            SAVINGS -> StringKey.MANUAL_ACCOUNTS_SAVINGS
            CREDIT_CARD -> StringKey.MANUAL_ACCOUNTS_CREDIT_CARD
        }
}

@Serializable
data class ManualAccountResponse(
    val id: String,
    val name: String,
    val type: ManualAccountType,
    val currency: String,
    val startingBalance: String,
    val currentBalance: String
)

@Serializable
data class CreateManualAccountRequest(
    val name: String,
    val type: ManualAccountType,
    val currency: String,
    val startingBalance: String
)

data class ManualAccountDraft(
    val name: String = "",
    val type: ManualAccountType = ManualAccountType.BANK_ACCOUNT,
    val currency: String = "GBP",
    val startingBalance: String = "0"
)

data class ManualAccountValidation(
    val nameError: StringKey? = null,
    val currencyError: StringKey? = null,
    val startingBalanceError: StringKey? = null
) {
    val isValid: Boolean
        get() = nameError == null && currencyError == null && startingBalanceError == null
}

// The proposed API/database contract allows 12 whole digits and two fractional digits.
// Parse into minor units rather than using floating-point money arithmetic.
internal fun parseManualMoney(input: String): Long? {
    val text = input.trim()
    if (text.isEmpty() || text.length > 64) return null
    if (!Regex("[+-]?(?:[0-9]+(?:[.,][0-9]{0,2})?|[.,][0-9]{1,2})").matches(text)) {
        return null
    }

    val negative = text.startsWith("-")
    val unsigned = text.removePrefix("-").removePrefix("+").replace(',', '.')
    val parts = unsigned.split('.')
    val whole = parts[0].trimStart('0').ifEmpty { "0" }
    if (whole.length > 12) return null
    val fraction = parts.getOrElse(1) { "" }.padEnd(2, '0')
    val minorUnits = whole.toLong() * 100 + fraction.toLong()
    return if (negative) -minorUnits else minorUnits
}

internal fun manualMoneyToDecimal(minorUnits: Long): String {
    // Only call with values returned by parseManualMoney: Long.MIN_VALUE is excluded.
    val absolute = if (minorUnits < 0) -minorUnits else minorUnits
    val sign = if (minorUnits < 0) "-" else ""
    return sign + (absolute / 100) + "." + (absolute % 100).toString().padStart(2, '0')
}

fun validateManualAccount(draft: ManualAccountDraft): ManualAccountValidation {
    val name = draft.name.trim()
    val money = parseManualMoney(draft.startingBalance)
    return ManualAccountValidation(
        nameError = when {
            name.isEmpty() -> StringKey.MANUAL_ACCOUNTS_NAME_REQUIRED
            name.length > 100 -> StringKey.MANUAL_ACCOUNTS_NAME_TOO_LONG
            else -> null
        },
        currencyError = if (draft.currency in CurrencyController.supportedCurrencies) {
            null
        } else {
            StringKey.MANUAL_ACCOUNTS_CURRENCY_INVALID
        },
        startingBalanceError = when {
            money == null -> StringKey.MANUAL_ACCOUNTS_BALANCE_INVALID
            money < 0 && draft.type != ManualAccountType.CREDIT_CARD ->
                StringKey.MANUAL_ACCOUNTS_BALANCE_NEGATIVE
            else -> null
        }
    )
}

fun ManualAccountDraft.toCreateRequestOrNull(): CreateManualAccountRequest? {
    if (!validateManualAccount(this).isValid) return null
    val minorUnits = parseManualMoney(startingBalance) ?: return null
    return CreateManualAccountRequest(
        name = name.trim(),
        type = type,
        currency = currency,
        startingBalance = manualMoneyToDecimal(minorUnits)
    )
}