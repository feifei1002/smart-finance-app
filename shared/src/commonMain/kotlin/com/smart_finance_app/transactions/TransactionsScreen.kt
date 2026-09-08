package com.smart_finance_app.transactions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.download
import smart_finance_app.shared.generated.resources.filter
import smart_finance_app.shared.generated.resources.search
import kotlin.math.ceil

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
) {
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
                    onLoadNextPage = onLoadNextPage
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
                    onPageSelected = onPageSelected
                )
            }
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
    onLoadNextPage: () -> Unit = {}
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var lastRequestedPage by remember { mutableStateOf(-1) }

    val listState = rememberLazyListState()

    val shouldLoadMore by remember(
        hasMore,
        isLoading,
        transactions.size,
        lastRequestedPage) {
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

        val matchesSearch =
            query.isBlank() ||
                    transaction.merchantName.contains(query, ignoreCase = true) ||
                    transaction.category.contains(query, ignoreCase = true) ||
                    transaction.accountName.contains(query, ignoreCase = true) ||
                    transaction.dateLabel.contains(query, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "Income" -> transaction.amount > 0
            "Expenses" -> transaction.amount < 0
            else -> true
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
                text = "Transactions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = "Search transactions"
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        painter = painterResource(Res.drawable.filter),
                        contentDescription = "Filter transactions"
                    )
                }
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = "Search transactions"
                    )
                },
                placeholder = { Text("Search transactions...") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { onFilterSelected("All") },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedFilter == "Income",
                onClick = { onFilterSelected("Income") },
                label = { Text("Income") }
            )
            FilterChip(
                selected = selectedFilter == "Expenses",
                onClick = { onFilterSelected("Expenses") },
                label = { Text("Expenses") }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isSyncing && transactions.isEmpty() -> {
                    item {
                        LoadingTransactionsState()
                    }
                }
                isLoading && transactions.isEmpty() -> {
                    item { LoadingTransactionsState() }
                }

                errorMessage != null && searchTransactions.isEmpty() -> {
                    item {
                        TransactionsInlineMessage(
                            message = errorMessage,
                            isError = true
                        )
                    }
                }

                searchTransactions.isEmpty() -> {
                    item {
                        TransactionsInlineMessage(
                            message = "No transactions available."
                        )
                    }
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

                        items(
                            items = dayTransactions,
                            key = { transaction -> transaction.id }
                        ) { transaction ->
                            MobileTransactionRow(transaction)
                        }
                    }

                    if (hasMore && isLoading) {
                        item {
                            LoadingTransactionsState()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileTransactionRow(transaction: TransactionUI) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
                text = "${transaction.category} • ${transaction.accountName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = formatAmount(transaction.amount, transaction.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.amount >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
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
    onPageSelected: (Int) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val searchTransactions = transactions.filter { transaction ->
        val query = searchQuery.trim()

        val matchesSearch =
            query.isBlank() ||
                    transaction.merchantName.contains(query, ignoreCase = true) ||
                    transaction.category.contains(query, ignoreCase = true) ||
                    transaction.accountName.contains(query, ignoreCase = true) ||
                    transaction.dateLabel.contains(query, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "Income" -> transaction.amount > 0
            "Expenses" -> transaction.amount < 0
            else -> true
        }

        matchesSearch && matchesFilter
    }
    val totalPages = ceil(totalCount / pageSize.toDouble()).toInt().coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Transactions",
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
                onValueChange = {
                    searchQuery = it
                    onPageSelected(0)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = "Search transactions"
                    )
                },
                placeholder = { Text("Search transactions...") }
            )

            OutlinedButton(
                onClick = {},
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.filter),
                    contentDescription = "Filter transactions",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Filters")
            }

            OutlinedButton(
                onClick = {},
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.download),
                    contentDescription = "Export transactions",
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
                label = { Text("All") }
            )

            FilterChip(
                selected = selectedFilter == "Income",
                onClick = {
                    onFilterSelected("Income")
                    onPageSelected(0)
                },
                label = { Text("Income") }
            )

            FilterChip(
                selected = selectedFilter == "Expenses",
                onClick = {
                    onFilterSelected("Expenses")
                    onPageSelected(0)
                },
                label = { Text("Expenses") }
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
                    LoadingTransactionsState()
                }

                isLoading -> {
                    LoadingTransactionsState()
                }

                errorMessage != null && searchTransactions.isEmpty() -> {
                    TransactionsInlineMessage(
                        message = errorMessage,
                        isError = true
                    )
                }

                searchTransactions.isEmpty() -> {
                    TransactionsInlineMessage(
                        message = "No transactions available."
                    )
                }

                else -> {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        TransactionTableHeader()

                        searchTransactions.forEach { transaction ->
                            TransactionTableRow(transaction)
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
                Text("Previous")
            }

            Text("${currentPage  + 1} / $totalPages")

            TextButton(
                enabled = currentPage  < totalPages - 1,
                onClick = { onPageSelected(currentPage + 1) }
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun TransactionTableHeader() {
    Row(
        modifier = Modifier.width(900.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TableCell("Date", 1f, bold = true)
        TableCell("Merchant", 1.5f, bold = true)
        TableCell("Category", 1.2f, bold = true)
        TableCell("Account", 1.5f, bold = true)
        TableCell("Amount", 1f, bold = true)
        TableCell("Actions", 0.8f, bold = true)
    }
}

@Composable
private fun TransactionTableRow(transaction: TransactionUI) {
    Row(
        modifier = Modifier.width(900.dp).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(transaction.dateLabel, 1f)
        MerchantTableCell(transaction, 1.5f)
        TableCell(transaction.category, 1.2f)
        TableCell(transaction.accountName, 1.5f)
        TableCell(formatAmount(transaction.amount, transaction.currency), 1f)
        TableCell("Edit", 0.8f)
    }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
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
private fun LoadingTransactionsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()

        Text(
            text = "Loading transactions...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionsInlineMessage(message: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MerchantLogo(merchantName: String, logoUrl: String?, modifier: Modifier = Modifier) {
    var imageFailed by remember(logoUrl) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
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

private fun formatAmount(amount: Double, currency: String): String {
    val sign = if (amount >= 0) "+" else "-"
    val symbol = when (currency.uppercase()) {
        "GBP" -> "£"
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency.uppercase()
    }
    return "$sign$symbol${kotlin.math.abs(amount)}"
}