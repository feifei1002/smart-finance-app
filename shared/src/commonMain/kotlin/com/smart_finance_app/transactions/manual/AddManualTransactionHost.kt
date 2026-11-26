package com.smart_finance_app.transactions.manual

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smart_finance_app.StringKey
import com.smart_finance_app.accounts.manual.ManualAccountResponse
import com.smart_finance_app.accounts.manual.ManualAccountsApi
import com.smart_finance_app.accounts.manual.ManualAccountsResult
import com.smart_finance_app.transactions.ImportedTransactionResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

@Composable
fun AddManualTransactionHost(
    authToken: String,
    transactionsApi: ManualTransactionsApi,
    accountsApi: ManualAccountsApi,
    onTransactionCreated: (ImportedTransactionResponse) -> Unit,
    content: @Composable (openAddTransaction: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()

    var showForm by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var typeName by rememberSaveable { mutableStateOf(ManualTransactionType.EXPENSE.name) }
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayIsoDate()) }
    var accountId by rememberSaveable { mutableStateOf("") }
    var pendingRequestJson by rememberSaveable { mutableStateOf<String?>(null) }
    var createRequestId by rememberSaveable { mutableStateOf(Uuid.random().toString()) }

    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<StringKey?>(null) }
    var accounts by remember { mutableStateOf(emptyList<ManualAccountResponse>()) }
    var accountsLoading by remember { mutableStateOf(false) }
    var accountsError by remember { mutableStateOf<StringKey?>(null) }
    var accountsVersion by remember { mutableStateOf(0) }

    val draft = ManualTransactionDraft(
        type = ManualTransactionType.entries.firstOrNull { it.name == typeName }
            ?: ManualTransactionType.EXPENSE,
        amount = amount,
        description = description,
        category = category,
        date = date,
        accountId = accountId
    )

    suspend fun loadAccounts() {
        val version = ++accountsVersion
        accountsLoading = true
        accountsError = null
        try {
            val loaded = mutableListOf<ManualAccountResponse>()
            var cursor: String? = null
            var pages = 0
            do {
                when (val result = accountsApi.getAccounts(authToken, cursor = cursor)) {
                    is ManualAccountsResult.Success -> {
                        loaded += result.accounts
                        cursor = result.nextCursor
                    }
                    is ManualAccountsResult.Failure -> {
                        if (version == accountsVersion) accountsError = result.message
                        return
                    }
                }
            } while (cursor != null && ++pages < 10)
            if (version != accountsVersion) return
            accounts = loaded.distinctBy { it.id }
            if (accountId.isBlank() && accounts.size == 1 && pendingRequestJson == null) {
                accountId = accounts.single().id
            }
        } finally {
            if (version == accountsVersion) accountsLoading = false
        }
    }

    fun openForm() {
        if (pendingRequestJson == null) {
            typeName = ManualTransactionType.EXPENSE.name
            amount = ""
            description = ""
            category = ""
            date = todayIsoDate()
            accountId = ""
            submitted = false
            saveError = null
            createRequestId = Uuid.random().toString()
        }
        showForm = true
    }

    fun closeForm() {
        if (!saving) showForm = false
    }

    fun saveTransaction() {
        if (saving) return
        submitted = true
        val accountIds = accounts.map { it.id }.toSet()
        val request = pendingRequestJson?.let {
            Json.decodeFromString<CreateManualTransactionRequest>(it)
        } ?: draft.toCreateRequestOrNull(accountIds) ?: return
        val requestId = createRequestId
        pendingRequestJson = Json.encodeToString(request)
        saving = true
        saveError = null
        scope.launch {
            try {
                when (val result = transactionsApi.createTransaction(authToken, request, requestId)) {
                    is CreateManualTransactionResult.Success -> {
                        pendingRequestJson = null
                        showForm = false
                        onTransactionCreated(result.transaction)
                    }
                    CreateManualTransactionResult.Conflict -> {
                        saveError = StringKey.MANUAL_TRANSACTIONS_CREATE_CONFLICT
                    }
                    is CreateManualTransactionResult.Failure -> {
                        saveError = result.message
                        if (result.message == StringKey.MANUAL_TRANSACTIONS_VALIDATION_ERROR ||
                            result.message == StringKey.MANUAL_TRANSACTIONS_ACCOUNT_NOT_FOUND
                        ) {
                            pendingRequestJson = null
                            createRequestId = Uuid.random().toString()
                        }
                        if (result.message == StringKey.MANUAL_TRANSACTIONS_ACCOUNT_NOT_FOUND) {
                            accountId = ""
                            loadAccounts()
                        }
                    }
                }
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(showForm, authToken) {
        if (showForm && authToken.isNotBlank()) loadAccounts()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        val formContent: @Composable (Modifier) -> Unit = { modifier ->
            ManualTransactionForm(
                draft = draft,
                onDraftChange = {
                    if (saving || pendingRequestJson != null) return@ManualTransactionForm
                    typeName = it.type.name
                    amount = it.amount
                    description = it.description
                    category = it.category
                    date = it.date
                    accountId = it.accountId
                    saveError = null
                },
                accounts = accounts,
                accountsLoading = accountsLoading,
                accountsError = accountsError,
                onRetryAccounts = { scope.launch { loadAccounts() } },
                showValidationErrors = submitted,
                isSaving = saving,
                retryPending = pendingRequestJson != null,
                serverError = saveError,
                compact = compact,
                onCancel = ::closeForm,
                onSave = ::saveTransaction,
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
            content(::openForm)

            if (showForm) {
                val maximumFormHeight = (maxHeight - 48.dp).coerceAtLeast(120.dp)
                Dialog(
                    onDismissRequest = ::closeForm,
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