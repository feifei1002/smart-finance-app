package com.smart_finance_app.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

@Composable
fun AccountSelectionScreen(
    accounts: List<SelectableBankAccountResponse>,
    selectedAccountIds: Set<String>,
    remainingSlots: Int,
    isLoading: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleAccount: (String) -> Unit,
    onConfirm: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compact) 20.dp else 40.dp,
                    end = if (compact) 20.dp else 40.dp,
                    top = if (compact) 48.dp else 32.dp,
                    bottom = if (compact) 24.dp else 32.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.CONNECT_BANK_ACCOUNT_SELECTION_TITLE),
                subtitle = appStringResource(StringKey.CONNECT_BANK_ACCOUNT_SELECTION_SUBTITLE),
                onBack = onBack,
                compact = compact
            )

            Text(
                text = appStringResource(StringKey.CONNECT_BANK_ACCOUNT_REMAINING_SLOTS)
                    .replace("%1\$d", remainingSlots.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (accounts.isEmpty()) {
                Text(
                    text = appStringResource(StringKey.CONNECT_BANK_ACCOUNT_NO_SELECTABLE_ACCOUNTS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    accounts.forEach { account ->
                        val selectionLimitReached = selectedAccountIds.size >= remainingSlots
                        val selected = account.accountId in selectedAccountIds
                        val enabled = !isSaving && (selected || !selectionLimitReached)

                        SelectableAccountRow(
                            account = account,
                            selected = account.accountId in selectedAccountIds,
                            enabled = enabled,
                            onClick = { onToggleAccount(account.accountId) }
                        )
                    }
                }
            }

            Button(
                onClick = onConfirm,
                enabled = selectedAccountIds.isNotEmpty() && !isLoading && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(appStringResource(StringKey.CONNECT_BANK_ACCOUNT_CONNECT_BUTTON))
                }
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(appStringResource(StringKey.COMMON_CANCEL))
            }
        }
    }
}

@Composable
private fun SelectableAccountRow(
    account: SelectableBankAccountResponse,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = account.bankName.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = account.bankName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "**** ${account.maskedNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = selected,
                onCheckedChange = { if (enabled) onClick() },
                enabled = enabled
            )
        }
    }
}