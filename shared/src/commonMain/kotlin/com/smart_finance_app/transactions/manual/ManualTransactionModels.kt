package com.smart_finance_app.transactions.manual

import com.smart_finance_app.StringKey
import com.smart_finance_app.accounts.manual.manualMoneyToDecimal
import com.smart_finance_app.accounts.manual.parseManualMoney
import com.smart_finance_app.transactions.TransactionCategories
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ManualTransactionType {
    @SerialName("income") INCOME,
    @SerialName("expense") EXPENSE;

    val labelKey: StringKey
        get() = when (this) {
            INCOME -> StringKey.MANUAL_TRANSACTIONS_TYPE_INCOME
            EXPENSE -> StringKey.MANUAL_TRANSACTIONS_TYPE_EXPENSE
        }

    val categories: List<String>
        get() = when (this) {
            INCOME -> listOf(
                TransactionCategories.INCOME,
                TransactionCategories.TRANSFERS,
                TransactionCategories.OTHERS
            )
            EXPENSE -> TransactionCategories.all.filter { it != TransactionCategories.INCOME }
        }
}

@Serializable
data class CreateManualTransactionRequest(
    val accountId: String,
    val type: ManualTransactionType,
    val amount: String,
    val description: String,
    val category: String,
    val date: String
)

data class ManualTransactionDraft(
    val type: ManualTransactionType = ManualTransactionType.EXPENSE,
    val amount: String = "",
    val description: String = "",
    val category: String = "",
    val date: String = todayIsoDate(),
    val accountId: String = ""
)

data class ManualTransactionValidation(
    val accountError: StringKey? = null,
    val amountError: StringKey? = null,
    val descriptionError: StringKey? = null,
    val categoryError: StringKey? = null,
    val dateError: StringKey? = null
) {
    val isValid: Boolean
        get() = accountError == null && amountError == null && descriptionError == null &&
                categoryError == null && dateError == null
}

fun validateManualTransaction(
    draft: ManualTransactionDraft,
    availableAccountIds: Set<String>,
    today: String = todayIsoDate()
): ManualTransactionValidation {
    val amountText = draft.amount.trim()
    val amount = parseManualMoney(amountText)
    val description = draft.description.trim()
    val date = parseIsoDateOrNull(draft.date)
    return ManualTransactionValidation(
        accountError = if (draft.accountId in availableAccountIds) null
        else StringKey.MANUAL_TRANSACTIONS_ACCOUNT_REQUIRED,
        amountError = when {
            amountText.isEmpty() -> StringKey.MANUAL_TRANSACTIONS_AMOUNT_REQUIRED
            amount == null -> StringKey.MANUAL_TRANSACTIONS_AMOUNT_INVALID
            amount <= 0 || amountText.startsWith("-") ->
                StringKey.MANUAL_TRANSACTIONS_AMOUNT_POSITIVE
            else -> null
        },
        descriptionError = when {
            description.isEmpty() -> StringKey.MANUAL_TRANSACTIONS_DESCRIPTION_REQUIRED
            description.length > 100 -> StringKey.MANUAL_TRANSACTIONS_DESCRIPTION_TOO_LONG
            else -> null
        },
        categoryError = if (draft.category in draft.type.categories) null
        else StringKey.MANUAL_TRANSACTIONS_CATEGORY_REQUIRED,
        dateError = when {
            date == null -> StringKey.MANUAL_TRANSACTIONS_DATE_INVALID
            draft.date > today -> StringKey.MANUAL_TRANSACTIONS_DATE_FUTURE
            else -> null
        }
    )
}

fun ManualTransactionDraft.toCreateRequestOrNull(
    availableAccountIds: Set<String>
): CreateManualTransactionRequest? {
    if (!validateManualTransaction(this, availableAccountIds).isValid) return null
    val minorUnits = parseManualMoney(amount) ?: return null
    return CreateManualTransactionRequest(
        accountId = accountId,
        type = type,
        amount = manualMoneyToDecimal(minorUnits),
        description = description.trim(),
        category = category,
        date = date
    )
}

fun todayIsoDate(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

internal fun parseIsoDateOrNull(text: String): LocalDate? =
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) null
    else runCatching { LocalDate.parse(text) }.getOrNull()

internal fun isoDateToPickerMillis(text: String): Long? =
    parseIsoDateOrNull(text)?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()

internal fun pickerMillisToIsoDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date.toString()