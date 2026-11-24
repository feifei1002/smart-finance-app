package com.smart_finance_app.accounts.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.CurrencyController
import com.smart_finance_app.currency.getCurrencySymbol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAccountForm(
    draft: ManualAccountDraft,
    onDraftChange: (ManualAccountDraft) -> Unit,
    showValidationErrors: Boolean,
    isSaving: Boolean,
    serverError: StringKey?,
    compact: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    retryPending: Boolean = false
) {
    val validation = validateManualAccount(draft)
    var typeExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    val nameError = validation.nameError.takeIf { showValidationErrors }
    val currencyError = validation.currencyError.takeIf { showValidationErrors }
    val balanceError = validation.startingBalanceError.takeIf { showValidationErrors }
    val signLabel = appStringResource(StringKey.MANUAL_ACCOUNTS_CHANGE_SIGN)
    val canEdit = !isSaving && !retryPending

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppPageHeader(
            title = appStringResource(StringKey.MANUAL_ACCOUNTS_ADD),
            compact = compact,
            onBack = if (compact && !isSaving) onCancel else null
        )
        serverError?.let { AppErrorMessage(appStringResource(it)) }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = { Text(appStringResource(StringKey.MANUAL_ACCOUNTS_NAME)) },
            singleLine = true,
            enabled = canEdit,
            isError = nameError != null,
            supportingText = nameError?.let { key -> { Text(appStringResource(key)) } },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = typeExpanded && canEdit,
            onExpandedChange = { if (canEdit) typeExpanded = it }
        ) {
            OutlinedTextField(
                value = appStringResource(draft.type.labelKey),
                onValueChange = {},
                readOnly = true,
                enabled = canEdit,
                label = { Text(appStringResource(StringKey.MANUAL_ACCOUNTS_TYPE)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, canEdit)
            )
            ExposedDropdownMenu(
                expanded = typeExpanded && canEdit,
                onDismissRequest = { typeExpanded = false }
            ) {
                ManualAccountType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(appStringResource(type.labelKey)) },
                        onClick = {
                            onDraftChange(draft.copy(type = type))
                            typeExpanded = false
                        },
                        enabled = canEdit,
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = currencyExpanded && canEdit,
            onExpandedChange = { if (canEdit) currencyExpanded = it }
        ) {
            OutlinedTextField(
                value = draft.currency,
                onValueChange = {},
                readOnly = true,
                enabled = canEdit,
                isError = currencyError != null,
                label = { Text(appStringResource(StringKey.MANUAL_ACCOUNTS_CURRENCY)) },
                supportingText = currencyError?.let { key -> { Text(appStringResource(key)) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, canEdit)
            )
            ExposedDropdownMenu(
                expanded = currencyExpanded && canEdit,
                onDismissRequest = { currencyExpanded = false }
            ) {
                CurrencyController.supportedCurrencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(currency) },
                        onClick = {
                            onDraftChange(draft.copy(currency = currency))
                            currencyExpanded = false
                        },
                        enabled = canEdit,
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.startingBalance,
            onValueChange = { onDraftChange(draft.copy(startingBalance = it)) },
            label = { Text(appStringResource(StringKey.MANUAL_ACCOUNTS_STARTING_BALANCE)) },
            prefix = {
                Text(getCurrencySymbol(draft.currency))
            },
            leadingIcon = if (draft.type == ManualAccountType.CREDIT_CARD) {
                {
                    IconButton(
                        enabled = canEdit,
                        modifier = Modifier.semantics { contentDescription = signLabel },
                        onClick = {
                            val amount = draft.startingBalance.trim()
                            onDraftChange(
                                draft.copy(
                                    startingBalance =
                                        if (amount.startsWith("-")) amount.removePrefix("-")
                                        else "-" + amount.removePrefix("+")
                                )
                            )
                        }
                    ) { Text("+/-") }
                }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = canEdit,
            isError = balanceError != null,
            supportingText = {
                if (balanceError != null) {
                    Text(appStringResource(balanceError))
                } else if (draft.type == ManualAccountType.CREDIT_CARD) {
                    Text(appStringResource(StringKey.MANUAL_ACCOUNTS_CREDIT_CARD_HELP))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(appStringResource(
                        if (retryPending) StringKey.COMMON_RETRY
                        else StringKey.MANUAL_ACCOUNTS_SAVE)
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
}