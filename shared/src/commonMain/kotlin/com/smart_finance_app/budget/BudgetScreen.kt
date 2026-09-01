package com.smart_finance_app.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smart_finance_app.dashboard.TransactionData
import com.smart_finance_app.dashboard.getCurrencySymbol
import com.smart_finance_app.transactions.TransactionCategories
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import org.jetbrains.compose.resources.vectorResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.close
import smart_finance_app.shared.generated.resources.delete
import smart_finance_app.shared.generated.resources.edit

// ── Category colours (matches DashboardState) ─────────────────────────────────

//val budgetCategories = listOf(
//    "Housing", "Food", "Transport", "Shopping", "Entertainment", "Other"
//)

val budgetCategories = TransactionCategories.all

//private val categoryColors = mapOf(
//    "Housing"       to Color(0xFF6366F1),
//    "Food"          to Color(0xFF22C55E),
//    "Transport"     to Color(0xFFF59E0B),
//    "Shopping"      to Color(0xFFEC4899),
//    "Entertainment" to Color(0xFF3B82F6),
//    "Other"         to Color(0xFF94A3B8)
//)

private val categoryColors = mapOf(
    TransactionCategories.FOOD_DINING to Color(0xFF22C55E),
    TransactionCategories.SHOPPING_PERSONAL to Color(0xFFEC4899),
    TransactionCategories.BILLS_HOUSING to Color(0xFF6366F1),
    TransactionCategories.ENTERTAINMENT_SUBSCRIPTIONS to Color(0xFF3B82F6),
    TransactionCategories.TRANSPORTATION to Color(0xFFF59E0B),
    TransactionCategories.MISCELLANEOUS to Color(0xFF94A3B8)
)

// Helper formatting function to avoid floating point layout bugs
private fun Double.formatCurrency(): String {
    val totalCents = kotlin.math.round(this * 100).toLong()
    val whole = totalCents / 100
    val cents = abs(totalCents % 100)
    return "$whole.${cents.toString().padStart(2, '0')}"
}

// ── Computed budget with spending ─────────────────────────────────────────────

data class BudgetWithSpending(
    val budget: BudgetData,
    val spent: Double,
    val color: Color
)

fun computeBudgetsWithSpending(
    budgets: List<BudgetData>,
    transactions: List<TransactionData>
): List<BudgetWithSpending> {
    // 1. Force UTC or standard date parsing so Web and Mobile behave identically
    val now = Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)

    return budgets.map { budget ->
        val relevant = transactions.filter { tx ->
            // Only count money going out. Income/credits should not count as budget spending.
            if (tx.amount >= 0) return@filter false
            // 2. Safely extract date parts regardless of ISO string lengths (e.g. "2026-07-28...")
            val dateOnly = tx.timestamp.split("T").firstOrNull() ?: tx.timestamp
            val parts = dateOnly.split("-")
            if (parts.size < 3) return@filter false

            val txYear  = parts[0].toIntOrNull() ?: return@filter false
            val txMonth = parts[1].toIntOrNull() ?: return@filter false
            val txDay   = parts[2].take(2).toIntOrNull() ?: return@filter false

            // 3. Case-insensitive period checking ("monthly", "Monthly", "MONTHLY")
            val periodNormalized = budget.period.trim().lowercase()

            val inPeriod = when (periodNormalized) {
                "monthly" -> txYear == now.year && txMonth == now.month.number
                "weekly"  -> {
                    val todayDayOfWeek = now.dayOfWeek.ordinal
                    val weekStart = now.date.minus(
                        kotlinx.datetime.DatePeriod(days = todayDayOfWeek)
                    )
                    val txDate = kotlinx.datetime.LocalDate(txYear, txMonth, txDay)
                    txDate >= weekStart && txDate <= now.date
                }
                // Fallback: If unknown period string, don't discard
                else -> true
            }

            if (!inPeriod) return@filter false

//            categoriseForBudget(tx.description, tx.merchantName) == budget.category
            TransactionCategories.normalize(tx.category) == budget.category
        }

        BudgetWithSpending(
            budget = budget,
            spent  = relevant.sumOf { abs(it.amount) },
            color  = categoryColors[budget.category] ?: Color(0xFF94A3B8)
        )
    }
}

fun categoriseForBudget(description: String, merchantName: String?): String {
    val text = (merchantName ?: description).lowercase()
    return when {
        text.contains("rent") || text.contains("mortgage") || text.contains("utilities")
                || text.contains("electricity") || text.contains("gas") || text.contains("water") -> "Housing"
        text.contains("tesco") || text.contains("sainsbury") || text.contains("waitrose")
                || text.contains("asda") || text.contains("aldi") || text.contains("lidl")
                || text.contains("grocery") || text.contains("food") || text.contains("restaurant")
                || text.contains("cafe") || text.contains("coffee") || text.contains("starbucks")
                || text.contains("mcdonald") || text.contains("deliveroo") || text.contains("uber eats") -> "Food"
        text.contains("uber") || text.contains("lyft") || text.contains("taxi")
                || text.contains("tfl") || text.contains("train") || text.contains("bus")
                || text.contains("fuel") || text.contains("petrol") || text.contains("parking") -> "Transport"
        text.contains("amazon") || text.contains("asos") || text.contains("ebay")
                || text.contains("zara") || text.contains("h&m") || text.contains("primark")
                || text.contains("shopping") || text.contains("store") -> "Shopping"
        text.contains("netflix") || text.contains("spotify") || text.contains("cinema")
                || text.contains("disney") || text.contains("apple") || text.contains("game")
                || text.contains("entertainment") -> "Entertainment"
        else -> "Other"
    }
}

// ── Main Budget Screen ─────────────────────────────────────────────────────────

@Composable
fun BudgetScreen(
    authToken: String,
    transactions: List<TransactionData>,
    currency: String,
    api: BudgetApi
) {
    val scope  = rememberCoroutineScope()
    val symbol = getCurrencySymbol(currency)

    var budgets     by remember { mutableStateOf<List<BudgetData>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var showDialog  by remember { mutableStateOf(false) }
    var editBudget  by remember { mutableStateOf<BudgetData?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    val budgetsWithSpending by derivedStateOf {
        computeBudgetsWithSpending(budgets, transactions)
    }

    suspend fun loadBudgets() {
        isLoading = true
        errorMsg  = null
        when (val result = api.getBudgets(authToken)) {
            is BudgetResult.Success -> budgets = result.data
            is BudgetResult.Failure -> errorMsg = result.message
        }
        isLoading = false
    }

    LaunchedEffect(authToken) { loadBudgets() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Budgets",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            errorMsg ?: "An error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { scope.launch { loadBudgets() } }) { Text("Retry") }
                    }
                }
            }
            budgets.isEmpty() -> {
                EmptyBudgetCard(onAddClick = {
                    editBudget = null
                    dialogError = null
                    showDialog = true
                })
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(budgetsWithSpending) { item ->
                        BudgetCard(
                            item   = item,
                            symbol = symbol,
                            onEdit = {
                                editBudget = item.budget
                                dialogError = null
                                showDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    when (val res = api.deleteBudget(authToken, item.budget.id)) {
                                        is BudgetResult.Success -> {
                                            errorMsg = null
                                            loadBudgets()
                                        }
                                        is BudgetResult.Failure -> errorMsg = res.message
                                    }
                                }
                            }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                editBudget = null
                                dialogError = null
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Budget", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Add / Edit dialog
    if (showDialog) {
        val usedCategories = remember(budgets, editBudget) {
            budgets.filter { editBudget == null || it.id != editBudget?.id }.map { it.category }
        }

        AddBudgetDialog(
            existing = editBudget,
            symbol = symbol,
            usedCategories = usedCategories,
            serverError = dialogError,
            onDismiss = {
                showDialog = false
                editBudget = null
                dialogError = null
            },
            onConfirm = { category, amount, period ->
                val currentEditing = editBudget
                scope.launch {
                    val result = if (currentEditing != null) {
                        api.updateBudget(authToken, currentEditing.id, amount, category, period)
                    } else {
                        api.createBudget(authToken, BudgetRequest(category, amount, period))
                    }
                    when (result) {
                        is BudgetResult.Success -> {
                            showDialog = false
                            editBudget = null
                            dialogError = null
                            loadBudgets()
                        }
                        is BudgetResult.Failure -> {
                            dialogError = result.message
                        }
                    }
                }
            }
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyBudgetCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Budget Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            "+",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Light
                        )
                    }
                    Text(
                        "Add a budget",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ── Budget card ───────────────────────────────────────────────────────────────

@Composable
fun BudgetCard(
    item: BudgetWithSpending,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (item.spent / item.budget.amount).toFloat().coerceIn(0f, 1f)
    val isOverBudget = item.spent > item.budget.amount
    val isWarning = progress >= 0.8f && !isOverBudget
    val barColor = when {
        isOverBudget -> Color(0xFFDC2626)
        isWarning    -> Color(0xFFF59E0B)
        else         -> item.color
    }
    val remaining = item.budget.amount - item.spent

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(10.dp).background(item.color, CircleShape))
                    Text(
                        item.budget.category,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.budget.period.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Row {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "Edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$symbol${item.spent.formatCurrency()} spent",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverBudget) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isOverBudget) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "of $symbol${item.budget.amount.formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.2f)
            )

            if (isOverBudget) {
                Text(
                    text = "Over budget by $symbol${(item.spent - item.budget.amount).formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "$symbol${remaining.formatCurrency()} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarning) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CompactBudgetProgressRow(
    item: BudgetWithSpending,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (item.spent / item.budget.amount).toFloat().coerceIn(0f, 1f)
    val isOver = item.spent > item.budget.amount

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(item.color, CircleShape)
                )

                Text(
                    text = item.budget.category,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$symbol${item.spent.formatCurrency()} / $symbol${item.budget.amount.formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOver) {
                        Color(0xFFDC2626)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete),
                        contentDescription = "Delete",
                        tint = Color(0xFFDC2626)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (isOver) Color(0xFFDC2626) else item.color,
            trackColor = item.color.copy(alpha = 0.2f)
        )
    }
}

// ── Add / Edit budget dialog ──────────────────────────────────────────────────
@Composable
fun AddBudgetDialog(
    existing: BudgetData?,
    symbol: String,
    usedCategories: List<String>,
    serverError: String?,
    onDismiss: () -> Unit,
    onConfirm: (category: String, amount: Double, period: String) -> Unit
) {
    val isEdit = existing != null

    // Pick first unused category if creating a new budget
    val defaultCategory = remember(usedCategories, existing) {
        existing?.category ?: (budgetCategories.firstOrNull { it !in usedCategories } ?: budgetCategories.first())
    }

    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    var amountText by remember { mutableStateOf(existing?.amount?.formatCurrency() ?: "") }
    var selectedPeriod by remember { mutableStateOf(existing?.period ?: "monthly") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row with Title and Close ('X') Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) "Edit Budget" else "Add Budget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.close),
                            contentDescription = "Close dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (serverError != null) {
                    Text(
                        text = serverError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Category selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        OutlinedButton(
                            onClick = { if (!isEdit) categoryExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isEdit
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        Modifier.size(10.dp).background(
                                            categoryColors[selectedCategory] ?: Color.Gray,
                                            CircleShape
                                        )
                                    )
                                    Text(selectedCategory)
                                }
                                Text("▾", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            budgetCategories.forEach { cat ->
                                val alreadyUsed = cat in usedCategories && cat != existing?.category
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                Modifier.size(10.dp).background(
                                                    categoryColors[cat] ?: Color.Gray,
                                                    CircleShape
                                                )
                                            )
                                            Text(
                                                cat,
                                                color = if (alreadyUsed)
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                            if (alreadyUsed) {
                                                Text(
                                                    " (set)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    enabled = !alreadyUsed,
                                    onClick = {
                                        selectedCategory = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Period selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Period",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("monthly", "weekly").forEach { period ->
                            val selected = selectedPeriod == period
                            OutlinedButton(
                                onClick = { selectedPeriod = period },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Text(
                                    text = period.replaceFirstChar { it.uppercase() },
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Amount text field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Limit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            amountError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text(symbol) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        singleLine = true
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val cleanedAmountText = amountText.trim().replace(",", ".")
                        val amount = cleanedAmountText.toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            amountError = "Please enter a valid amount"
                            return@Button
                        }
                        onConfirm(selectedCategory, amount, selectedPeriod)
                    }) {
                        Text(if (isEdit) "Save" else "Add Budget")
                    }
                }
            }
        }
    }
}