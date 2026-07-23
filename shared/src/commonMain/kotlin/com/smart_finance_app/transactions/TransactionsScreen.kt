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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.download
import smart_finance_app.shared.generated.resources.filter
import smart_finance_app.shared.generated.resources.search
import kotlin.math.ceil
import kotlin.math.min

data class TransactionUI(val id: String, val dateLabel: String, val merchantName: String,
                         val category: String, val accountName: String, val amount: Double)

@Composable
fun TransactionsScreen(
    transactions: List<TransactionUI>,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        when {
            compact -> {
                MobileTransactionsList(
                    transactions = transactions,
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
            else -> {
                DesktopTransactionsTable(
                    transactions = transactions,
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }
}

@Composable
private fun MobileTransactionsList(
    transactions: List<TransactionUI>,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

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
                onValueChange = { searchQuery = it },
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
                onClick = { selectedFilter = "All" },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedFilter == "Income",
                onClick = { selectedFilter = "Income" },
                label = { Text("Income") }
            )
            FilterChip(
                selected = selectedFilter == "Expenses",
                onClick = { selectedFilter = "Expenses" },
                label = { Text("Expenses") }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
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
                    searchTransactions.groupBy { it.dateLabel }.forEach { (date, items) ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        items.forEach { transaction -> MobileTransactionRow(transaction) }
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
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = transaction.merchantName.first().uppercase(),
                    fontWeight = FontWeight.Bold
                )
            }
        }

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
            text = formatAmount(transaction.amount),
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
    errorMessage: String? = null
) {
    var page by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val searchTransactions = transactions.filter { transaction ->
        val query = searchQuery.trim()

        val matchesSearch =
            query.isBlank() ||
                    transaction.merchantName.contains(query, ignoreCase = true) ||
                    transaction.category.contains(query, ignoreCase = true) ||
                    transaction.accountName.contains(query, ignoreCase = true) ||
                    transaction.dateLabel.contains(query, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "Income" -> transaction.amount >= 0
            "Expenses" -> transaction.amount < 0
            else -> true
        }

        matchesSearch && matchesFilter
    }
    val pageSize = 6
    val totalPages = ceil(searchTransactions.size / pageSize.toDouble()).toInt().coerceAtLeast(1)
    val start = page * pageSize
    val pageItems = searchTransactions.subList(start, min(start + pageSize, searchTransactions.size))

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
                    page = 0
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
                onClick = {
                    selectedFilter = "All"
                    page = 0
                },
                label = { Text("All") }
            )

            FilterChip(
                selected = selectedFilter == "Income",
                onClick = {
                    selectedFilter = "Income"
                    page = 0
                },
                label = { Text("Income") }
            )

            FilterChip(
                selected = selectedFilter == "Expenses",
                onClick = {
                    selectedFilter = "Expenses"
                    page = 0
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

                        pageItems.forEach { transaction ->
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
                enabled = page > 0,
                onClick = { page-- }
            ) {
                Text("Previous")
            }

            Text("${page + 1} / $totalPages")

            TextButton(
                enabled = page < totalPages - 1,
                onClick = { page++ }
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
        TableCell(transaction.merchantName, 1.5f)
        TableCell(transaction.category, 1.2f)
        TableCell(transaction.accountName, 1.5f)
        TableCell(formatAmount(transaction.amount), 1f)
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

private fun formatAmount(amount: Double): String {
    val sign = if (amount >= 0) "+" else "-"
    return "$sign£${kotlin.math.abs(amount)}"
}