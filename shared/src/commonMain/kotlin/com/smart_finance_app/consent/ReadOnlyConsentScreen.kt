package com.smart_finance_app.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.lock
import smart_finance_app.shared.generated.resources.check_circle
import smart_finance_app.shared.generated.resources.cancel

data class ConsentItem(
    val text: String,
    val allowed: Boolean
)

@Composable
fun ReadOnlyConsentScreen(
    errorMessage: String? = null,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    val consentItems = listOf(
        ConsentItem("Read your account balances", allowed = true),
        ConsentItem("Read your account transactions", allowed = true),
        ConsentItem("Cannot move money", allowed = false),
        ConsentItem("Cannot make payments", allowed = false)
    )

    var agreed by remember { mutableStateOf(false) }
    val boxScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.lock),
                contentDescription = "Secure",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Read-only Access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "We only access your data to help you understand your finances.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            consentItems.forEach { ConsentRow(it) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your data is secure and encrypted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
                .verticalScroll(boxScrollState)
        ) {
            Text(
                text = "DATA ACCESS & CONSENT AGREEMENT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Please read the following terms carefully before connecting your financial accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            ConsentSection(
                number = "1.", title = "Account Connection Consent",
                points = listOf(
                    "You authorise us to securely connect to your selected financial institutions via Open Banking or equivalent regulated APIs.",
                    "You explicitly consent to linking your bank and financial accounts for the purpose of data retrieval only."
                )
            )
            ConsentSection(
                number = "2.", title = "Read-Only Access Limitation",
                points = listOf(
                    "This application operates in read-only mode at all times.",
                    "We are strictly unable to initiate, approve, modify, or reverse any transactions.",
                    "We do not hold permission to transfer funds, make payments, or alter your accounts in any capacity."
                )
            )
            ConsentSection(
                number = "3.", title = "Data We Access",
                preamble = "Subject to your authorisation, we may retrieve the following data:",
                points = listOf(
                    "Account balances",
                    "Transaction history (income and expenditure)",
                    "Merchant and payee names",
                    "Transaction dates and amounts",
                    "Account type and identifiers (masked where applicable)"
                )
            )
            ConsentSection(
                number = "4.", title = "Purpose of Data Use",
                preamble = "Your financial data is used solely for the following purposes:",
                points = listOf(
                    "Aggregating financial information across your connected accounts",
                    "Displaying dashboards, charts, and financial summaries",
                    "Categorising transactions (e.g. income, subscriptions, groceries)",
                    "Providing spending insights and financial analytics",
                    "Delivering personalised financial overview features"
                )
            )
            ConsentSection(
                number = "5.", title = "No Financial Advice Disclaimer",
                points = listOf(
                    "The information provided by this application is for informational purposes only.",
                    "Nothing within this application constitutes financial, tax, investment, or legal advice.",
                    "You remain solely responsible for any financial decisions made based on information displayed."
                )
            )
            ConsentSection(
                number = "6.", title = "Data Refresh & Continuous Access",
                points = listOf(
                    "You consent to periodic data refreshes via API to maintain an up-to-date dashboard.",
                    "Certain institutions may trigger automatic updates upon new transaction activity.",
                    "The frequency of data retrieval is subject to limitations imposed by third-party bank APIs."
                )
            )
            ConsentSection(
                number = "7.", title = "Data Storage & Retention",
                points = listOf(
                    "Retrieved financial data is stored securely to provide historical insights and continuity of service.",
                    "Data is retained only for as long as your account remains active or as required for service delivery.",
                    "You may request deletion of your personal financial data at any time via the app settings."
                )
            )
            ConsentSection(
                number = "8.", title = "Third-Party API Providers",
                points = listOf(
                    "We utilise regulated Open Banking providers or authorised third-party data aggregators to facilitate secure account connections.",
                    "Your data may pass through these providers exclusively for the purpose of secure retrieval.",
                    "We do not sell, share, or disclose your financial data to advertisers or unauthorised parties."
                )
            )
            ConsentSection(
                number = "9.", title = "Security & Encryption",
                points = listOf(
                    "All data transmissions are encrypted using industry-standard protocols (e.g. TLS 1.2 or above).",
                    "Sensitive data is stored in encrypted form at rest.",
                    "Access to your data is restricted to authorised system processes only."
                )
            )
            ConsentSection(
                number = "10.", title = "User Control & Revocation",
                points = listOf(
                    "You may disconnect any linked account at any time via the application settings.",
                    "You may revoke consent directly through your bank where supported by your institution.",
                    "Revoking consent will immediately cease all future data retrieval for the affected account."
                )
            )
            ConsentSection(
                number = "11.", title = "No Transaction Capability Clause",
                preamble = "Important: This system is strictly incapable of the following:",
                points = listOf(
                    "Sending or receiving money on your behalf",
                    "Initiating or authorising payments of any kind",
                    "Modifying standing orders, direct debits, or scheduled payments",
                    "Changing account settings or personal details",
                    "Only read-only data retrieval endpoints are accessed"
                )
            )
            ConsentSection(
                number = "12.", title = "Accuracy Disclaimer",
                points = listOf(
                    "Financial data is sourced directly from third-party financial institutions.",
                    "We are not responsible for delays, missing transactions, or inaccuracies originating from source data providers."
                )
            )
            ConsentSection(
                number = "13.", title = "User Responsibility",
                points = listOf(
                    "You are responsible for maintaining the security of your login credentials and connected devices.",
                    "You agree not to grant access to this application to unauthorised parties."
                )
            )
            ConsentSection(
                number = "14.", title = "Compliance Acknowledgement",
                points = listOf(
                    "You acknowledge that this service functions as a financial data aggregation tool only.",
                    "You consent to the processing of your financial data in accordance with applicable data protection legislation, including UK GDPR and the Data Protection Act 2018."
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = { agreed = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buildAnnotatedString {
                    append("I have read and agree to the ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Data Access & Consent Agreement")
                    }
                    append(" above.")
                },
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onContinue,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Continue")
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ConsentSection(
    number: String,
    title: String,
    preamble: String? = null,
    points: List<String>
) {
    val letters = ('a'..'z').toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "$number $title",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (preamble != null) {
            Text(
                text = preamble,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        points.forEachIndexed { index, point ->
            Row(
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${letters[index]}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(item: ConsentItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (item.allowed) Color(0xFF16A34A) else Color(0xFFDC2626),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (item.allowed) Res.drawable.check_circle else Res.drawable.cancel
                ),
                contentDescription = if (item.allowed) "Allowed" else "Not allowed",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}