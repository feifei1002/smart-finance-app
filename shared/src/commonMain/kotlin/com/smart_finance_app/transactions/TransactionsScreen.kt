package com.smart_finance_app.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.download
import smart_finance_app.shared.generated.resources.edit
import smart_finance_app.shared.generated.resources.filter
import smart_finance_app.shared.generated.resources.search
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import kotlin.math.ceil
import com.smart_finance_app.localiseCategory
import kotlinx.coroutines.launch

data class TransactionUI(
    val id: String,
    val dateLabel: String,
    val merchantName: String,
    val category: String,
    val accountName: String,
    val amount: Double,
    val currency: String,
    val merchantLogoUrl: String? = null,
    val accountId: String? = null
)

@Composable
fun TransactionsScreen(
    transactions: List<TransactionUI>,
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    errorMessage: String? = null,
    currentPage: Int = 0,
    totalCount: Int = 0,
    pageSize: Int = 25,
    hasMore: Boolean = false,
    selectedFilter: String = "All",
    onFilterSelected: (String) -> Unit = {},
    onLoadNextPage: () -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
    isUpdatingCategory: Boolean = false,
    categoryUpdateError: String? = null,
    onUpdateCategory: suspend (String, String) -> Boolean = { _, _ -> false },
    onDismissCategoryUpdateError: () -> Unit = {},
) {

    var editingTransaction by remember { mutableStateOf<TransactionUI?>(null) }

    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        when {
            compact -> {
                MobileTransactionsList(
                    transactions = transactions,
                    isLoading = isLoading,
                    isSyncing = isSyncing,
                    errorMessage = errorMessage,
                    hasMore = hasMore,
                    selectedFilter = selectedFilter,
                    onFilterSelected = onFilterSelected,
                    onLoadNextPage = onLoadNextPage,
                    onEditCategory = { transaction: TransactionUI ->
                        onDismissCategoryUpdateError()
                        editingTransaction = transaction
                    }
                )
            }
            else -> {
                DesktopTransactionsTable(
                    transactions = transactions,
                    isLoading = isLoading,
                    isSyncing = isSyncing,
                    errorMessage = errorMessage,
                    currentPage = currentPage,
                    totalCount = totalCount,
                    pageSize = pageSize,
                    selectedFilter = selectedFilter,
                    onFilterSelected = onFilterSelected,
                    onPageSelected = onPageSelected,
                    onEditCategory = { transaction: TransactionUI ->
                        editingTransaction = transaction
                    }
                )
            }
        }

        editingTransaction?.let { transaction ->
            EditTransactionCategoryDialog(
                transaction = transaction,
                isSaving = isUpdatingCategory,
                errorMessage = categoryUpdateError,
                onDismiss = {
                    editingTransaction = null
                    onDismissCategoryUpdateError()
                },
                onCategorySelected = { category ->
                    scope.launch {
                        val success = onUpdateCategory(transaction.id, category)

                        if (success) {
                            editingTransaction = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MobileTransactionsList(
    transactions: List<TransactionUI>,
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    errorMessage: String? = null,
    hasMore: Boolean = false,
    selectedFilter: String = "All",
    onFilterSelected: (String) -> Unit = {},
    onLoadNextPage: () -> Unit = {},
    onEditCategory: (TransactionUI) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var lastRequestedPage by remember { mutableStateOf(-1) }

    val listState = rememberLazyListState()

    // Filter keys are internal constants — keep them as English strings for
    // logic comparisons. Only the displayed labels are localised.
    val filterAllLabel      = appStringResource(StringKey.TRANSACTIONS_FILTER_ALL)
    val filterIncomeLabel   = appStringResource(StringKey.TRANSACTIONS_FILTER_INCOME)
    val filterExpensesLabel = appStringResource(StringKey.TRANSACTIONS_FILTER_EXPENSES)
    val emptyLabel          = appStringResource(StringKey.TRANSACTIONS_EMPTY)
    val loadingLabel        = appStringResource(StringKey.TRANSACTIONS_LOADING)
    val searchPlaceholder   = appStringResource(StringKey.TRANSACTIONS_SEARCH_PLACEHOLDER)

    val shouldLoadMore by remember(
        hasMore, isLoading, transactions.size, lastRequestedPage
    ) {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            hasMore &&
                    !isLoading &&
                    lastVisibleItem != null &&
                    totalItems > 0 &&
                    lastVisibleItem.index >= totalItems - 3 &&
                    lastRequestedPage != transactions.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            lastRequestedPage = transactions.size
            onLoadNextPage()
        }
    }

    val searchTransactions = transactions.filter { transaction ->
        val query = searchQuery.trim()
        val matchesSearch = query.isBlank() ||
                transaction.merchantName.contains(query, ignoreCase = true) ||
                transaction.category.contains(query, ignoreCase = true) ||
                transaction.accountName.contains(query, ignoreCase = true) ||
                transaction.dateLabel.contains(query, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Income"   -> transaction.amount > 0
            "Expenses" -> transaction.amount < 0
            else       -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appStringResource(StringKey.TRANSACTIONS_TITLE),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = searchPlaceholder
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        painter = painterResource(Res.drawable.filter),
                        contentDescription = null
                    )
                }
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = null
                    )
                },
                placeholder = { Text(searchPlaceholder) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { onFilterSelected("All") },
                label = { Text(filterAllLabel) }
            )
            FilterChip(
                selected = selectedFilter == "Income",
                onClick = { onFilterSelected("Income") },
                label = { Text(filterIncomeLabel) }
            )
            FilterChip(
                selected = selectedFilter == "Expenses",
                onClick = { onFilterSelected("Expenses") },
                label = { Text(filterExpensesLabel) }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isSyncing && transactions.isEmpty() -> item { LoadingTransactionsState(loadingLabel) }
                isLoading && transactions.isEmpty()  -> item { LoadingTransactionsState(loadingLabel) }
                errorMessage != null && searchTransactions.isEmpty() -> item {
                    TransactionsInlineMessage(message = errorMessage, isError = true)
                }
                searchTransactions.isEmpty() -> item {
                    TransactionsInlineMessage(message = emptyLabel)
                }
                else -> {
                    searchTransactions.groupBy { it.dateLabel }.forEach { (date, dayTransactions) ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(items = dayTransactions, key = { it.id }) { transaction ->
                            MobileTransactionRow(
                                transaction = transaction,
                                onEditCategory = onEditCategory
                            )
                        }
                    }
                    if (hasMore && isLoading) {
                        item { LoadingTransactionsState(loadingLabel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileTransactionRow(
    transaction: TransactionUI,
    onEditCategory: (TransactionUI) -> Unit
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MerchantLogo(
            merchantName = transaction.merchantName,
            logoUrl = transaction.merchantLogoUrl,
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchantName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${localiseCategory(transaction.category)} • ${transaction.accountName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatAmount(transaction.amount, transaction.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.amount >= 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )

        IconButton(
            onClick = { onEditCategory(transaction) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.edit),
                contentDescription = "Edit transaction category",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DesktopTransactionsTable(
    transactions: List<TransactionUI>,
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    errorMessage: String? = null,
    currentPage: Int = 0,
    totalCount: Int = 0,
    pageSize: Int = 6,
    selectedFilter: String = "All",
    onFilterSelected: (String) -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
    onEditCategory: (TransactionUI) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filterAllLabel      = appStringResource(StringKey.TRANSACTIONS_FILTER_ALL)
    val filterIncomeLabel   = appStringResource(StringKey.TRANSACTIONS_FILTER_INCOME)
    val filterExpensesLabel = appStringResource(StringKey.TRANSACTIONS_FILTER_EXPENSES)
    val emptyLabel          = appStringResource(StringKey.TRANSACTIONS_EMPTY)
    val loadingLabel        = appStringResource(StringKey.TRANSACTIONS_LOADING)
    val searchPlaceholder   = appStringResource(StringKey.TRANSACTIONS_SEARCH_PLACEHOLDER)
    val previousLabel       = appStringResource(StringKey.TRANSACTIONS_PREVIOUS)
    val nextLabel           = appStringResource(StringKey.TRANSACTIONS_NEXT)
    val dateHeader          = appStringResource(StringKey.TRANSACTIONS_COL_DATE)
    val merchantHeader      = appStringResource(StringKey.TRANSACTIONS_COL_MERCHANT)
    val categoryHeader      = appStringResource(StringKey.TRANSACTIONS_COL_CATEGORY)
    val accountHeader       = appStringResource(StringKey.TRANSACTIONS_COL_ACCOUNT)
    val amountHeader        = appStringResource(StringKey.TRANSACTIONS_COL_AMOUNT)
    val actionsHeader       = appStringResource(StringKey.TRANSACTIONS_COL_ACTIONS)
    val editLabel           = appStringResource(StringKey.TRANSACTIONS_EDIT)

    val searchTransactions = transactions.filter { transaction ->
        val query = searchQuery.trim()
        val matchesSearch = query.isBlank() ||
                transaction.merchantName.contains(query, ignoreCase = true) ||
                transaction.category.contains(query, ignoreCase = true) ||
                transaction.accountName.contains(query, ignoreCase = true) ||
                transaction.dateLabel.contains(query, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Income"   -> transaction.amount > 0
            "Expenses" -> transaction.amount < 0
            else       -> true
        }
        matchesSearch && matchesFilter
    }

    val totalPages = ceil(totalCount / pageSize.toDouble()).toInt().coerceAtLeast(1)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = appStringResource(StringKey.TRANSACTIONS_TITLE),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; onPageSelected(0) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = null
                    )
                },
                placeholder = { Text(searchPlaceholder) }
            )

            OutlinedButton(
                onClick = {},
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.filter),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                // "Filters" and "Export" are UI chrome labels — add to strings.xml
                // if you want them localised; for MVP they're fine as-is
                Text("Filters")
            }

            OutlinedButton(
                onClick = {},
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Export")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { onFilterSelected("All") },
                label = { Text(filterAllLabel) }
            )
            FilterChip(
                selected = selectedFilter == "Income",
                onClick = { onFilterSelected("Income"); onPageSelected(0) },
                label = { Text(filterIncomeLabel) }
            )
            FilterChip(
                selected = selectedFilter == "Expenses",
                onClick = { onFilterSelected("Expenses"); onPageSelected(0) },
                label = { Text(filterExpensesLabel) }
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            when {
                isSyncing && transactions.isEmpty() -> {
                    LoadingTransactionsState(loadingLabel)
                }

                isLoading -> {
                    LoadingTransactionsState(loadingLabel)
                }

                errorMessage != null && searchTransactions.isEmpty() -> {
                    TransactionsInlineMessage(
                        message = errorMessage,
                        isError = true
                    )
                }

                searchTransactions.isEmpty() -> {
                    TransactionsInlineMessage(message = emptyLabel)
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        TransactionTableHeader(
                            dateHeader = dateHeader,
                            merchantHeader = merchantHeader,
                            categoryHeader = categoryHeader,
                            accountHeader = accountHeader,
                            amountHeader = amountHeader,
                            actionsHeader = actionsHeader
                        )

                        searchTransactions.forEach { transaction ->
                            TransactionTableRow(
                                transaction = transaction,
                                editLabel = editLabel,
                                onEditCategory = onEditCategory
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = currentPage > 0,
                onClick = { onPageSelected(currentPage - 1) }
            ) {
                Text(previousLabel)
            }
            Text("${currentPage + 1} / $totalPages")
            TextButton(
                enabled = currentPage < totalPages - 1,
                onClick = { onPageSelected(currentPage + 1) }
            ) {
                Text(nextLabel)
            }
        }
    }
}

@Composable
private fun TransactionTableHeader(
    dateHeader: String,
    merchantHeader: String,
    categoryHeader: String,
    accountHeader: String,
    amountHeader: String,
    actionsHeader: String
) {
    Row(
        modifier = Modifier.width(900.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TableCell(dateHeader,     1f,   bold = true)
        TableCell(merchantHeader, 1.5f, bold = true)
        TableCell(categoryHeader, 1.2f, bold = true)
        TableCell(accountHeader,  1.5f, bold = true)
        TableCell(amountHeader,   1f,   bold = true)
        TableCell(actionsHeader,  0.8f, bold = true, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TransactionTableRow(
    transaction: TransactionUI,
    editLabel: String,
    onEditCategory: (TransactionUI) -> Unit
) {
    Row(
        modifier = Modifier.width(900.dp).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(transaction.dateLabel, 1f)
        MerchantTableCell(transaction, 1.5f)
        TableCell(localiseCategory(transaction.category), 1.2f)
        TableCell(transaction.accountName, 1.5f)

        TableCell(
            text = formatAmount(transaction.amount, transaction.currency),
            weight = 1f
        )

        Box(
            modifier = Modifier.weight(0.8f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { onEditCategory(transaction) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.edit),
                    contentDescription = editLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = textAlign
    )
}

@Composable
private fun RowScope.MerchantTableCell(transaction: TransactionUI, weight: Float) {
    Row(
        modifier = Modifier.weight(weight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MerchantLogo(
            merchantName = transaction.merchantName,
            logoUrl = transaction.merchantLogoUrl,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = transaction.merchantName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun LoadingTransactionsState(loadingLabel: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = loadingLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionsInlineMessage(message: String, isError: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MerchantLogo(
    merchantName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(logoUrl) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (!logoUrl.isNullOrBlank() && !imageFailed) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = "$merchantName logo",
                    contentScale = ContentScale.Fit,
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = merchantName.firstOrNull()?.uppercase() ?: "?",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun EditTransactionCategoryDialog(
    transaction: TransactionUI,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val categories = selectableCategories(transaction)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(appStringResource(StringKey.TRANSACTIONS_EDIT_CATEGORY)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = transaction.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatAmount(transaction.amount, transaction.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (transaction.amount >= 0) {
                            Color(0xFF6F58A8)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                categories.forEach { category ->
                    val isCurrent = category == transaction.category

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable(enabled = !isSaving) {
                                onCategorySelected(category)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = localiseCategory(category),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(appStringResource(StringKey.COMMON_CANCEL))
            }
        }
    )
}

private fun selectableCategories(transaction: TransactionUI): List<String> {
    return if (transaction.amount < 0) {
        TransactionCategories.all.filter { it != TransactionCategories.INCOME }
    } else {
        TransactionCategories.all
    }
}

private fun formatAmount(amount: Double, currency: String): String {
    val sign   = if (amount >= 0) "+" else "-"
    val symbol = when (currency.uppercase()) {
        "GBP" -> "£"
        "USD" -> "$"
        "EUR" -> "€"
        else  -> currency.uppercase()
    }
    return "$sign$symbol${kotlin.math.abs(amount)}"
}