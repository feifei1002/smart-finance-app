package com.smart_finance_app.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

@Composable
internal fun AccountDetailsScreen(
    account: ConnectedAccount,
    onBack: () -> Unit,
    onDisconnectClick: () -> Unit
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
            title = appStringResource(StringKey.ACCOUNT_DETAILS_TITLE),
            subtitle = appStringResource(StringKey.ACCOUNT_DETAILS_SUBTITLE),
            onBack = onBack,
            compact = compact
        )
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = account.bankName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "**** ${account.maskedNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = appStringResource(StringKey.ACCOUNTS_STATUS) + ": ${
                            if (account.isConnected) {
                                appStringResource(StringKey.ACCOUNTS_STATUS_CONNECTED)
                            } else {
                                appStringResource(StringKey.ACCOUNTS_STATUS_DISCONNECTED)
                            }
                        }",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    OutlinedButton(
                        onClick = onDisconnectClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(appStringResource(StringKey.ACCOUNTS_DISCONNECT_BANK))
                    }
                }
            }
        }
    }
}