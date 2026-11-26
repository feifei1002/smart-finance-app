package com.smart_finance_app.transactions.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.StringKey
import com.smart_finance_app.accounts.manual.ManualAccountResponse
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.getCurrencySymbol
import com.smart_finance_app.localiseCategory
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.calendar_month

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTransactionForm(
    draft: ManualTransactionDraft,
    onDraftChange: (ManualTransactionDraft) -> Unit,
    accounts: List<ManualAccountResponse>,
    accountsLoading: Boolean,
    accountsError: StringKey?,
    onRetryAccounts: () -> Unit,
    showValidationErrors: Boolean,
    isSaving: Boolean,
    serverError: StringKey?,
    compact: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    retryPending: Boolean = false
) {
    val accountIds = accounts.map { it.id }.toSet()
    val validation = validateManualTransaction(draft, accountIds)
    val canEdit = !isSaving && !retryPending
    val selectedAccount = accounts.firstOrNull { it.id == draft.accountId }
    val noAccounts = !accountsLoading && accountsError == null && accounts.isEmpty()

    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun <T> T?.ifShown(): T? = takeIf { showValidationErrors }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppPageHeader(
            title = appStringResource(StringKey.MANUAL_TRANSACTIONS_ADD),
            compact = compact,
            onBack = if (compact && !isSaving) onCancel else null
        )
        serverError?.let { AppErrorMessage(appStringResource(it)) }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val types = listOf(ManualTransactionType.EXPENSE, ManualTransactionType.INCOME)
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = draft.type == type,
                    enabled = canEdit,
                    onClick = {
                        if (draft.type != type) {
                            onDraftChange(
                                draft.copy(
                                    type = type,
                                    category = draft.category.takeIf { it in type.categories }
                                        ?: ""
                                )
                            )
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                ) { Text(appStringResource(type.labelKey)) }
            }
        }

        val amountError = validation.amountError.ifShown()
        OutlinedTextField(
            value = draft.amount,
            onValueChange = { onDraftChange(draft.copy(amount = it)) },
            label = { Text(appStringResource(StringKey.MANUAL_TRANSACTIONS_AMOUNT)) },
            prefix = selectedAccount?.let { account ->
                { Text(getCurrencySymbol(account.currency)) }
            },
            placeholder = { Text("0.00") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = canEdit,
            isError = amountError != null,
            supportingText = amountError?.let { key -> { Text(appStringResource(key)) } },
            modifier = Modifier.fillMaxWidth()
        )

        val descriptionError = validation.descriptionError.ifShown()
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChange(draft.copy(description = it)) },
            label = { Text(appStringResource(StringKey.MANUAL_TRANSACTIONS_DESCRIPTION)) },
            singleLine = true,
            enabled = canEdit,
            isError = descriptionError != null,
            supportingText = descriptionError?.let { key -> { Text(appStringResource(key)) } },
            modifier = Modifier.fillMaxWidth()
        )

        val categoryError = validation.categoryError.ifShown()
        ExposedDropdownMenuBox(
            expanded = categoryExpanded && canEdit,
            onExpandedChange = { if (canEdit) categoryExpanded = it }
        ) {
            OutlinedTextField(
                value = draft.category.takeIf { it.isNotEmpty() }?.let { localiseCategory(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = canEdit,
                isError = categoryError != null,
                label = { Text(appStringResource(StringKey.MANUAL_TRANSACTIONS_CATEGORY)) },
                supportingText = categoryError?.let { key -> { Text(appStringResource(key)) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, canEdit)
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded && canEdit,
                onDismissRequest = { categoryExpanded = false }
            ) {
                draft.type.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(localiseCategory(category)) },
                        onClick = {
                            onDraftChange(draft.copy(category = category))
                            categoryExpanded = false
                        },
                        enabled = canEdit
                    )
                }
            }
        }

        val dateError = validation.dateError.ifShown()
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft.date,
                onValueChange = {},
                readOnly = true,
                enabled = canEdit,
                isError = dateError != null,
                label = { Text(appStringResource(StringKey.MANUAL_TRANSACTIONS_DATE)) },
                supportingText = dateError?.let { key -> { Text(appStringResource(key)) } },
                trailingIcon = {
                    Icon(painterResource(Res.drawable.calendar_month), contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                Modifier.matchParentSize()
                    .clickable(
                        enabled = canEdit,
                        onClickLabel = appStringResource(StringKey.MANUAL_TRANSACTIONS_PICK_DATE)
                    ) { showDatePicker = true }
            )
        }

        val accountError = validation.accountError.ifShown()
        when {
            accountsLoading && accounts.isEmpty() -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    appStringResource(StringKey.MANUAL_TRANSACTIONS_ACCOUNTS_LOADING),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            accountsError != null && accounts.isEmpty() -> Column {
                AppErrorMessage(appStringResource(accountsError))
                TextButton(onClick = onRetryAccounts) {
                    Text(appStringResource(StringKey.COMMON_RETRY))
                }
            }

            noAccounts -> Text(
                appStringResource(StringKey.MANUAL_TRANSACTIONS_NO_ACCOUNTS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            else -> ExposedDropdownMenuBox(
                expanded = accountExpanded && canEdit,
                onExpandedChange = { if (canEdit) accountExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAccount?.let { "${it.name} (${it.currency})" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = canEdit,
                    isError = accountError != null,
                    label = { Text(appStringResource(StringKey.MANUAL_TRANSACTIONS_ACCOUNT)) },
                    supportingText = accountError?.let { key -> { Text(appStringResource(key)) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, canEdit)
                )
                ExposedDropdownMenu(
                    expanded = accountExpanded && canEdit,
                    onDismissRequest = { accountExpanded = false }
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(account.name)
                                    Text(
                                        appStringResource(account.type.labelKey) +
                                                " | " + account.currency,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onDraftChange(draft.copy(accountId = account.id))
                                accountExpanded = false
                            },
                            enabled = canEdit
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = !isSaving && !noAccounts,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        appStringResource(
                            if (retryPending) StringKey.COMMON_RETRY
                            else StringKey.MANUAL_TRANSACTIONS_SAVE
                        )
                    )
                }
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(appStringResource(StringKey.COMMON_CANCEL)) }
        }
    }

    if (showDatePicker) {
        val today = todayIsoDate()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoDateToPickerMillis(draft.date),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    pickerMillisToIsoDate(utcTimeMillis) <= today
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDraftChange(draft.copy(date = pickerMillisToIsoDate(it)))
                    }
                    showDatePicker = false
                }) { Text(appStringResource(StringKey.MANUAL_TRANSACTIONS_DATE_OK)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(appStringResource(StringKey.COMMON_CANCEL))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}