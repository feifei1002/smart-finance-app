package com.smart_finance_app.accounts.manual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.CurrencyController
import com.smart_finance_app.currency.getCurrencySymbol
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.add

@Composable
fun ManualAccountsScreen(authToken: String, api: ManualAccountsApi) {
    // Changing the user or API disposes outstanding UI work and resets account data.
    key(authToken, api) { ManualAccountsContent(authToken, api) }
}

@Composable
private fun ManualAccountsContent(authToken: String, api: ManualAccountsApi) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(emptyList<ManualAccountResponse>()) }
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<StringKey?>(null) }
    var loadVersion by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<StringKey?>(null) }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(ManualAccountType.BANK_ACCOUNT.name) }
    var currency by rememberSaveable { mutableStateOf(CurrencyController.currentCurrency) }
    var balance by rememberSaveable { mutableStateOf("0") }

    val draft = ManualAccountDraft(
        name = name,
        type = ManualAccountType.entries.firstOrNull { it.name == typeName }
            ?: ManualAccountType.BANK_ACCOUNT,
        currency = currency,
        startingBalance = balance
    )

    suspend fun loadAccounts() {
        val version = ++loadVersion
        loading = true
        loadError = null
        try {
            when (val result = api.getAccounts(authToken)) {
                is ManualAccountsResult.Success -> if (version == loadVersion) {
                    accounts = result.accounts
                    loaded = true
                }
                is ManualAccountsResult.Failure -> if (version == loadVersion) {
                    loadError = result.message
                }
            }
        } finally {
            if (version == loadVersion) loading = false
        }
    }

    fun openForm() {
        name = ""
        typeName = ManualAccountType.BANK_ACCOUNT.name
        currency = CurrencyController.currentCurrency
        balance = "0"
        submitted = false
        saveError = null
        showForm = true
    }

    fun saveAccount() {
        if (saving) return
        submitted = true
        val request = draft.toCreateRequestOrNull() ?: return
        saving = true
        saveError = null
        scope.launch {
            try {
                when (val result = api.createAccount(authToken, request)) {
                    is CreateManualAccountResult.Success -> {
                        accounts = (listOf(result.account) + accounts).distinctBy { it.id }
                        loaded = true
                        showForm = false
                        // A follow-up GET failure must not reopen a successfully saved form.
                        loadAccounts()
                    }
                    is CreateManualAccountResult.Failure -> saveError = result.message
                }
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(authToken, api) { loadAccounts() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        val formContent: @Composable (Modifier) -> Unit = { modifier ->
            ManualAccountForm(
                draft = draft,
                onDraftChange = {
                    name = it.name
                    typeName = it.type.name
                    currency = it.currency
                    balance = it.startingBalance
                    saveError = null
                },
                showValidationErrors = submitted,
                isSaving = saving,
                serverError = saveError,
                compact = compact,
                onCancel = { if (!saving) showForm = false },
                onSave = ::saveAccount,
                modifier = modifier
            )
        }

        if (compact && showForm) {
            formContent(
                Modifier.fillMaxSize().imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(
                    horizontal = if (compact) 20.dp else 40.dp,
                    vertical = if (compact) 48.dp else 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppPageHeader(
                    title = appStringResource(StringKey.NAV_ACCOUNTS),
                    compact = compact
                )
                Button(onClick = ::openForm, enabled = !saving && authToken.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(Res.drawable.add), contentDescription = null)
                        Text(appStringResource(StringKey.MANUAL_ACCOUNTS_ADD))
                    }
                }
                loadError?.let {
                    AppErrorMessage(appStringResource(it))
                    TextButton(
                        enabled = !loading,
                        onClick = { scope.launch { loadAccounts() } }
                    ) { Text(appStringResource(StringKey.COMMON_RETRY)) }
                }
                when {
                    loading && accounts.isEmpty() -> Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    loaded && accounts.isEmpty() && loadError == null -> Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            appStringResource(StringKey.MANUAL_ACCOUNTS_EMPTY_TITLE),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            appStringResource(StringKey.MANUAL_ACCOUNTS_EMPTY_BODY),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    accounts.isNotEmpty() -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(280.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(accounts, key = { it.id }) { account ->
                            ManualAccountCard(account)
                        }
                    }
                }
            }

            if (showForm) {
                val maximumFormHeight = (maxHeight - 48.dp).coerceAtLeast(120.dp)
                Dialog(
                    onDismissRequest = { if (!saving) showForm = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.padding(24.dp)
                            .widthIn(max = 520.dp).fillMaxWidth()
                            .heightIn(max = maximumFormHeight),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        formContent(
                            Modifier.verticalScroll(rememberScrollState()).padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualAccountCard(account: ManualAccountResponse) {
    val balanceText = formatManualAccountBalance(
        amount = account.currentBalance,
        currency = account.currency
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                account.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                appStringResource(account.type.labelKey) + " | " + account.currency,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                appStringResource(StringKey.MANUAL_ACCOUNTS_CURRENT_BALANCE),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                balanceText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatManualAccountBalance(
    amount: String,
    currency: String
): String {
    val match = Regex("""([+-]?)([0-9]+)(?:\.([0-9]{1,2}))?""")
        .matchEntire(amount.trim())
        ?: return "-"

    val whole = match.groupValues[2].trimStart('0').ifEmpty { "0" }
    val fraction = match.groupValues[3].padEnd(2, '0')

    val isZero = whole == "0" && fraction == "00"
    val sign = if (match.groupValues[1] == "-" && !isZero) "-" else ""
    val symbol = getCurrencySymbol(currency)

    return "$sign$symbol$whole.$fraction"
}
